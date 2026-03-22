package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.exception.UserNotFoundException;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserProviderListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public TableDTO getAllProviderUsers(int page, int size, String sortBy, String sortDir, String q) {
        List<Column> columns = DisplayFieldScanner.getColumns(UserProviderListDTO.class);
        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<UserProviderListDTO> dtos = userRepository.findAll().stream()
                .map(this::toProviderListDto)
                .filter(dto -> needle.isEmpty()
                        || containsIgnoreCase(dto.getEmail(), needle)
                        || containsIgnoreCase(dto.getFullName(), needle))
                .sorted(providerUserSort(sortBy, dir))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int total = dtos.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<UserProviderListDTO> pageDtos = dtos.subList(from, to);
        List<Map<String, Object>> rows = DisplayFieldScanner.getDataMaps(pageDtos, columns);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        return TableDTO.builder()
                .columns(columns)
                .rows(rows)
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(totalPages)
                .totalElements(total)
                .build();
    }

    public TableDTO getAllTenantUsers(UUID tenantId, int page, int size, String sortBy, String sortDir, String q) {
        return buildUsersTable(
                userRepository.findDistinctByUserTenantMembershipsTenantId(tenantId),
                page,
                size,
                sortBy,
                sortDir,
                q
        );
    }

    public UserDTO getUserById(String id) {
        return toUserDto(findUserById(id));
    }

    public UserDTO getTenantUserById(UUID tenantId, String id) {
        UUID userId = parseUserId(id);
        User user = userRepository.findByIdAndUserTenantMembershipsTenantId(userId, tenantId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toUserDto(user);
    }

    public UserDTO updateUser(UserDTO userDTO) {
        User user = findUserById(userDTO.getId());
        applyUserUpdate(user, userDTO);
        userRepository.save(user);
        return toUserDto(user);
    }

    public UserDTO updateTenantUser(UUID tenantId, UserDTO userDTO) {
        UUID userId = parseUserId(userDTO.getId());
        User user = userRepository.findByIdAndUserTenantMembershipsTenantId(userId, tenantId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        applyUserUpdate(user, userDTO);
        userRepository.save(user);
        return toUserDto(user);
    }

    private TableDTO buildUsersTable(List<User> users, int page, int size, String sortBy, String sortDir, String q) {
        List<Column> columns = DisplayFieldScanner.getColumns(UserListDTO.class);
        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<UserListDTO> dtos = users.stream()
                .map(this::toListDto)
                .filter(dto -> needle.isEmpty()
                        || containsIgnoreCase(dto.getEmail(), needle)
                        || containsIgnoreCase(dto.getFullName(), needle))
                .sorted(userSort(sortBy, dir))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int total = dtos.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<UserListDTO> pageDtos = dtos.subList(from, to);
        List<Map<String, Object>> rows = DisplayFieldScanner.getDataMaps(pageDtos, columns);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        return TableDTO.builder()
                .columns(columns)
                .rows(rows)
                .pageNumber(safePage)
                .pageSize(safeSize)
                .totalPages(totalPages)
                .totalElements(total)
                .build();
    }

    private UserDTO toUserDto(User user) {
        var profile = user.getProfile();
        return UserDTO.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
                .gender(profile != null ? profile.getGender() : null)
                .dialCode(profile != null ? profile.getDialCode() : null)
                .phoneNumber(profile != null ? profile.getPhoneNumber() : null)
                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .build();
    }

    private User findUserById(String id) {
        return userRepository.findById(parseUserId(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private UUID parseUserId(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid_user_id");
        }
    }

    private void applyUserUpdate(User user, UserDTO userDTO) {
        user.setEmail(userDTO.getEmail());
        user.setFullName(userDTO.getFullName());
        user.setGivenName(userDTO.getGivenName());
        user.setFamilyName(userDTO.getFamilyName());

        if (user.getProfile() != null) {
            user.getProfile().setDateOfBirth(userDTO.getDateOfBirth());
            user.getProfile().setGender(userDTO.getGender());
            user.getProfile().setDialCode(userDTO.getDialCode());
            user.getProfile().setPhoneNumber(userDTO.getPhoneNumber());
            user.getProfile().setAvatarUrl(userDTO.getAvatarUrl());
        }
    }

    private UserListDTO toListDto(User u) {
        return UserListDTO.builder()
                .id(u.getId().toString())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .build();
    }

    private UserProviderListDTO toProviderListDto(User u) {
        return UserProviderListDTO.builder()
                .id(u.getId().toString())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .build();
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Comparator<UserListDTO> userSort(String sortBy, Sort.Direction dir) {
        Comparator<UserListDTO> comparator;
        if ("email".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    dto -> dto.getEmail() == null ? "" : dto.getEmail().toLowerCase(Locale.ROOT)
            );
        } else if ("fullName".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    dto -> dto.getFullName() == null ? "" : dto.getFullName().toLowerCase(Locale.ROOT)
            );
        } else {
            comparator = Comparator.comparing(UserListDTO::getId);
        }
        return dir == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }

    private Comparator<UserProviderListDTO> providerUserSort(String sortBy, Sort.Direction dir) {
        Comparator<UserProviderListDTO> comparator;
        if ("email".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    dto -> dto.getEmail() == null ? "" : dto.getEmail().toLowerCase(Locale.ROOT)
            );
        } else if ("fullName".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    dto -> dto.getFullName() == null ? "" : dto.getFullName().toLowerCase(Locale.ROOT)
            );
        } else {
            comparator = Comparator.comparing(UserProviderListDTO::getId);
        }
        return dir == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }
}
