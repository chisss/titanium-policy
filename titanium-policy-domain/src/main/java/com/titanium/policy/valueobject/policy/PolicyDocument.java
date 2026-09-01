package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyEnum;

public record PolicyDocument(String docId, String electronicDocNo, String paperDocNo, LocalDateTime docGenerateTime,
                             PolicyEnum.SignatureStatus signatureStatus, String docStorageUrl) {
}
