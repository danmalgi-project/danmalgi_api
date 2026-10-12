package com.danmalgi.backend.user.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.danmalgi.backend.global.infrastructure.image.ImageProcessor;
import com.danmalgi.backend.global.infrastructure.r2.R2Uploader;
import com.danmalgi.backend.user.domain.exception.DuplicatedUserException;
import com.danmalgi.backend.user.domain.exception.UserNotFoundException;
import com.danmalgi.backend.user.domain.model.User;
import com.danmalgi.backend.user.domain.model.UserStatus;
import com.danmalgi.backend.user.repository.entity.UserEntity;
import com.danmalgi.backend.user.repository.persistence.UserJpaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserJpaRepository userJpaRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final R2Uploader r2Uploader;
    private final ImageProcessor imageProcessor;

    @Cacheable(value = "user", key = "#p0")
    public User getUser(Long userId) {
        User user = userJpaRepository.findById(userId)
                .map(UserEntity::toDomainUser)
                .orElseThrow(() -> new UserNotFoundException("user not found"));
        // 캐시에 담기기 전에 조립한다. Redis user:{id} 는 chat/webrtc 가 직접 읽는
        // 공유 계약이므로 캐시 히트/미스가 같은 값을 내야 한다.
        user.applyPublicProfileImageUrl(r2Uploader);
        return user;
    }

    public List<User> getUsers(List<Long> userIds) {
        List<User> result = userJpaRepository.findAllById(userIds).stream()
                .map(UserEntity::toDomainUser)
                .toList();

        Set<Long> foundIds = result.stream().map(User::getId).collect(Collectors.toSet());
        for (Long id : userIds) {
            if (!foundIds.contains(id)) {
                throw new UserNotFoundException("user not found: " + id);
            }
        }

        // multiSet 이전에 조립해야 캐시에 public URL 이 들어간다.
        result.forEach(user -> user.applyPublicProfileImageUrl(r2Uploader));

        Map<String, Object> toCache = result.stream()
                .collect(Collectors.toMap(u -> "user:" + u.getId(), u -> u));
        redisTemplate.opsForValue().multiSet(toCache);
        toCache.keySet().forEach(key -> redisTemplate.expire(key, Duration.ofMinutes(2)));

        return result;
    }

    public void verifyNameAndTag(String name, String tag) {
        if (userJpaRepository.findByNameAndTag(name, tag).isPresent()) {
            throw new DuplicatedUserException("name and tag already in use");
        }
    }

    // 친구 요청 전에 대상을 미리 보여주는 조회라, 못 찾는 것도 정상 결과로 보고 빈 list 를 낸다.
    // name+tag 는 유니크라 지금은 0~1건이지만, 이름만으로 찾는 확장을 위해 list 로 둔다.
    public List<User> searchUser(Long requesterId, String name, String tag) {
        // 가입 시 trim 해서 저장하므로(AuthService.registerUser) 검색도 같은 기준으로 맞춘다.
        // VerifyNameAndTag·AddRelationship 은 아직 trim 하지 않는다.
        return userJpaRepository.findByNameAndTag(name.trim(), tag.trim())
                // 자기 자신은 친구 후보가 아니다.
                .filter(entity -> !entity.getId().equals(requesterId))
                // 탈퇴·정지·가입 미완료 유저는 친구 후보로 보여주지 않는다.
                .filter(entity -> entity.getStatus() == UserStatus.ACTIVE.getNumber())
                .map(entity -> {
                    User user = entity.toDomainUser();
                    user.applyPublicProfileImageUrl(r2Uploader);
                    return List.of(user);
                })
                .orElseGet(List::of);
    }

    public User uploadProfile(Long userId, byte[] image, String extension) {
        UserEntity userEntity = userJpaRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user not found"));

        byte[] webp = imageProcessor.toWebp(image);
        String key = r2Uploader.upload(userId, webp, "webp");

        userEntity.updateProfileImageUrl(key);
        userJpaRepository.save(userEntity);

        redisTemplate.delete("user:" + userId);

        User user = userEntity.toDomainUser();
        user.applyPublicProfileImageUrl(r2Uploader);
        return user;
    }
}
