package cn.cordys.mmba;

public record MmbaCredential(
        String apiBaseUrl,
        String companyCode,
        String appKey,
        String secret
) {}
