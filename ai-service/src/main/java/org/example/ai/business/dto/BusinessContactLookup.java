package org.example.ai.business.dto;

public record BusinessContactLookup(BusinessContactStatus status, BusinessContact contact) {

    public static BusinessContactLookup available(BusinessContact contact) {
        return new BusinessContactLookup(BusinessContactStatus.AVAILABLE, contact);
    }

    public static BusinessContactLookup status(BusinessContactStatus status) {
        return new BusinessContactLookup(status, null);
    }
}
