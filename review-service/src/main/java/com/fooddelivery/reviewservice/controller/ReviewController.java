package com.fooddelivery.reviewservice.controller;

import com.fooddelivery.reviewservice.dto.request.CreateReviewRequest;
import com.fooddelivery.reviewservice.dto.request.UpdateReviewRequest;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.service.ReviewService;
import com.fooddelivery.reviewservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "Rate a delivered order, read, edit and delete your own review (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Review a delivered order",
            description = "Overall rating, up to 4 tags, a comment and optionally a rating per dish of the order. "
                    + "404 for an unknown order or one that is not yours, 422 when it is not delivered, "
                    + "409 when it already has a review")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody CreateReviewRequest request, CallerInfo caller) {
        return reviewService.create(request, caller);
    }

    @Operation(summary = "My review of an order", description = "404 when I have not reviewed it yet")
    @GetMapping("/orders/{orderId}")
    public ReviewResponse getMine(@PathVariable Long orderId, CallerInfo caller) {
        return reviewService.getMine(orderId, caller);
    }

    @Operation(summary = "Edit my review",
            description = "Same rules as writing one; the tags and dish ratings sent replace the saved ones (a dish left "
                    + "out is no longer rated), and dishes must belong to the original order. An optional version "
                    + "that is no longer the review's current one gives 409. 404 for an unknown review or one that is "
                    + "not mine, 422 for a dish that is not on the order")
    @PutMapping("/{reviewId}")
    public ReviewResponse update(@PathVariable Long reviewId, @Valid @RequestBody UpdateReviewRequest request,
                                 CallerInfo caller) {
        return reviewService.update(reviewId, request, caller);
    }

    @Operation(summary = "Delete my review",
            description = "The order can be reviewed again afterwards. 404 for an unknown review or one that is not mine")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long reviewId, CallerInfo caller) {
        reviewService.delete(reviewId, caller);
    }
}
