package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TenantMemberUpsertRequest {
    @Size(max = 36)
    public String id;
    @Size(max = 36)
    public String userId;
    @Email
    @Size(max = 255)
    public String email;
    @Size(max = 255)
    public String fullName;
    public Boolean isDefault;
    @Size(max = 50)
    public List<String> roles;
}
