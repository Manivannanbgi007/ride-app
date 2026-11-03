package com.fooddelivery.userservice.mapper;

import com.fooddelivery.userservice.dto.request.AddressRequest;
import com.fooddelivery.userservice.dto.response.AddressResponse;
import com.fooddelivery.userservice.dto.response.InternalAddressResponse;
import com.fooddelivery.userservice.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(Long userId, AddressRequest request) {
        Address address = new Address();
        address.setUserId(userId);
        copyFields(request, address);
        return address;
    }

    public void updateEntity(Address address, AddressRequest request) {
        copyFields(request, address);
    }

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getType(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }

    public InternalAddressResponse toInternalResponse(Address address) {
        return new InternalAddressResponse(
                address.getId(),
                address.getUserId(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getLatitude(),
                address.getLongitude());
    }

    private void copyFields(AddressRequest request, Address address) {
        address.setType(request.type());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2() == null || request.line2().isBlank() ? null : request.line2().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(request.country().trim());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
    }
}
