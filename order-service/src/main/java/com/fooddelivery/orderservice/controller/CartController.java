package com.fooddelivery.orderservice.controller;

import com.fooddelivery.orderservice.dto.request.CartItemRequest;
import com.fooddelivery.orderservice.dto.request.CouponCodeRequest;
import com.fooddelivery.orderservice.dto.response.CartResponse;
import com.fooddelivery.orderservice.service.CartService;
import com.fooddelivery.orderservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The calling customer's cart. Requires an access token with the role CUSTOMER. */
@Tag(name = "Cart", description = "One cart per customer, items of one restaurant, live prices")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "My cart with current prices, totals and anything that blocks checkout")
    @GetMapping
    public CartResponse getCart(CallerInfo caller) {
        return cartService.getCart(caller.userId());
    }

    @Operation(summary = "Add an item or change its quantity (409 if the cart holds another restaurant's items)")
    @PutMapping("/items/{menuItemId}")
    public CartResponse setItemQuantity(@PathVariable Long menuItemId, @Valid @RequestBody CartItemRequest request,
                                        CallerInfo caller) {
        return cartService.setItemQuantity(caller.userId(), menuItemId, request.quantity());
    }

    @Operation(summary = "Remove an item")
    @DeleteMapping("/items/{menuItemId}")
    public CartResponse removeItem(@PathVariable Long menuItemId, CallerInfo caller) {
        return cartService.removeItem(caller.userId(), menuItemId);
    }

    @Operation(summary = "Apply a coupon (422 with the reason if it cannot be used for this cart)",
            description = "The discount is worked out again whenever the cart is shown. If the cart later stops "
                    + "qualifying, the coupon stays but blocks checkout until the cart qualifies again or it is removed.")
    @PutMapping("/coupon")
    public CartResponse applyCoupon(@Valid @RequestBody CouponCodeRequest request, CallerInfo caller) {
        return cartService.applyCoupon(caller.userId(), request.code());
    }

    @Operation(summary = "Remove the coupon")
    @DeleteMapping("/coupon")
    public CartResponse removeCoupon(CallerInfo caller) {
        return cartService.removeCoupon(caller.userId());
    }

    @Operation(summary = "Empty the cart (and remove its coupon)")
    @DeleteMapping
    public ResponseEntity<Void> clearCart(CallerInfo caller) {
        cartService.clearCart(caller.userId());
        return ResponseEntity.noContent().build();
    }
}
