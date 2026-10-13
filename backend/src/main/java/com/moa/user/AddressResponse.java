package com.moa.user;

public record AddressResponse(Long id, String recipientName, String recipientPhone, String zipCode,
        String address1, String address2, String deliveryNote, boolean isDefault) {

    static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getRecipientName(), address.getRecipientPhone(),
                address.getZipCode(), address.getAddress1(), address.getAddress2(), address.getDeliveryNote(),
                address.isDefault());
    }
}
