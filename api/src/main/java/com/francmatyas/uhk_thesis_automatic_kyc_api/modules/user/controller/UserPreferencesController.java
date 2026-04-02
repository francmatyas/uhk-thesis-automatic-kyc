package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.GetUserPreferencesDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.PreferencesOptionsDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UpdateUserPreferencesRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.UserPreferencesDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service.UserPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {
    private final UserPreferencesService userPreferencesService;

    @GetMapping
    public ResponseEntity<?> getCurrentUserPreferences(
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        GetUserPreferencesDTO preferences = userPreferencesService.getUserPreferences(currentUser.getId());
        return ResponseEntity.ok(preferences);
    }

    @PutMapping
    public ResponseEntity<?> updateCurrentUserPreferences(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateUserPreferencesRequest request
    ) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserPreferencesDTO preferences = userPreferencesService.updateUserPreferences(
                currentUser.getId(),
                request
        );
        return ResponseEntity.ok(preferences);
    }

    @GetMapping("/options")
    public ResponseEntity<PreferencesOptionsDTO> getPreferencesOptions() {
        PreferencesOptionsDTO options = userPreferencesService.getPreferencesOptions();
        return ResponseEntity.ok(options);
    }
}

