package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.BaseEntity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserPreferences;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserProfile;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.EncryptedStringConverter;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.FieldCrypto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, exclude = {"userRoles", "profile", "preferences", "userTenantMemberships"})
@SQLDelete(sql = "UPDATE users SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
public class User extends BaseEntity implements UserDetails {
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String givenName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String middleName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String familyName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String fullName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "is_provider_user")
    private boolean isProviderUser = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserTenantMembership> userTenantMemberships = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserTenantRole> userTenantRoles = new LinkedHashSet<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true, orphanRemoval = true)
    private UserProfile profile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true, orphanRemoval = true)
    private UserPreferences preferences;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Zploštění rolí do oprávnění typu ROLE_
        return userRoles.stream()
                .map(ur -> "ROLE_" + ur.getRole().getName())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Soft-deleted uživatele považovat za deaktivované
        return enabled && !isDeleted();
    }

    @PrePersist
    @PreUpdate
    private void syncFullName() {
        email = FieldCrypto.normalizeEmail(email);
        emailHash = FieldCrypto.hashEmail(email);

        StringBuilder builder = new StringBuilder();

        if (givenName != null && !givenName.isBlank()) {
            builder.append(givenName.trim());
        }

        if (middleName != null && !middleName.isBlank()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append(middleName.trim());
        }

        if (familyName != null && !familyName.isBlank()) {
            if (!builder.isEmpty()) builder.append(" ");
            builder.append(familyName.trim());
        }

        this.fullName = builder.toString().trim();
    }

    public Set<String> getRoleNames() {
        return userRoles.stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toSet());
    }

    // Pomocný setter pro synchronizaci obousměrné asociace
    public void setProfile(UserProfile profile) {
        if (Objects.equals(this.profile, profile)) {
            return;
        }

        // Odpojení původního profilu
        if (this.profile != null) {
            UserProfile old = this.profile;
            this.profile = null;
            if (old.getUser() == this) {
                old.setUser(null);
            }
        }

        // Připojení nového profilu
        if (profile != null) {
            this.profile = profile;
            if (profile.getUser() != this) {
                profile.setUser(this);
            }
        }
    }

    // Pomocný setter pro synchronizaci obousměrné asociace
    public void setPreferences(UserPreferences preferences) {
        if (Objects.equals(this.preferences, preferences)) {
            return;
        }

        // Odpojení původních preferencí
        if (this.preferences != null) {
            UserPreferences old = this.preferences;
            this.preferences = null;
            if (old.getUser() == this) {
                old.setUser(null);
            }
        }

        // Připojení nových preferencí
        if (preferences != null) {
            this.preferences = preferences;
            if (preferences.getUser() != this) {
                preferences.setUser(this);
            }
        }
    }
}
