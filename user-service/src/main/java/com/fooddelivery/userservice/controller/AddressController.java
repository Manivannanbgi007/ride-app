package com.fooddelivery.userservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.userservice.dto.request.AddressRequest;
import com.fooddelivery.userservice.dto.response.AddressResponse;
import com.fooddelivery.userservice.service.AddressService;
import com.fooddelivery.userservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My addresses", description = "The calling user's delivery addresses")
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "List my addresses (default first)")
    @GetMapping
    public List<AddressResponse> getMyAddresses(CallerInfo caller) {
        return addressService.getAddresses(caller.userId());
    }

    @Operation(summary = "Add an address")
    @PostMapping
    public ResponseEntity<AddressResponse> addAddress(@Valid @RequestBody AddressRequest request, CallerInfo caller) {
        AddressResponse address = addressService.addAddress(caller.userId(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/users/me/addresses/" + address.id()))
                .body(address);
    }

    @Operation(summary = "Update an address")
    @PutMapping("/{addressId}")
    public AddressResponse updateAddress(@PathVariable Long addressId,
                                         @Valid @RequestBody AddressRequest request,
                                         CallerInfo caller) {
        return addressService.updateAddress(caller.userId(), addressId, request);
    }

    @Operation(summary = "Make an address the default")
    @PutMapping("/{addressId}/default")
    public AddressResponse setDefaultAddress(@PathVariable Long addressId, CallerInfo caller) {
        return addressService.setDefaultAddress(caller.userId(), addressId);
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId, CallerInfo caller) {
        addressService.deleteAddress(caller.userId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
