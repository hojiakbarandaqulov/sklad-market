package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ApiResponse;
import org.example.dto.order.*;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
@Tag(name = "Buyurtmalar", description = "Xaridor va sotuvchi uchun buyurtma yaratish, ko'rish va boshqarish")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Amal bajarildi; natija data ichida"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "So'rov maydoni yoki filtr noto'g'ri"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT yo'q yoki yaroqsiz"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Rol, profileId yoki kompaniya egaligi yetarli emas"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Buyurtma, mahsulot yoki kompaniya topilmadi"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Qoldiq yetishmaydi, kalit to'qnashdi yoki holat o'zgarishi mumkin emas"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Bog'liq xizmat ishlamayapti yoki service-account sozlanmagan")
})
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Buyurtma yaratish va mahsulotlarni rezervlash",
            description = "BUYER yoki SELLER. Bitta kompaniya va bitta valyuta. Narx backenddan olinadi. "
                    + "Aloqa uzilganda aynan shu idempotencyKey bilan takrorlang. Qoldiq yetishmasa 409, bog'liq xizmat ishlamasa 503.")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.successResponse(orderService.create(request));
    }

    @GetMapping
    @Operation(summary = "Xaridorning o'z buyurtmalari",
            description = "Xaridor token ichidagi profileId orqali aniqlanadi. companyId ixtiyoriy. page 1 dan boshlanadi, perPage 1–100.")
    public ApiResponse<Page<OrderResponse>> buyer(@Valid @ParameterObject @ModelAttribute OrderFilter filter) {
        return ApiResponse.successResponse(orderService.getBuyerOrders(filter));
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Sotuvchiga kelgan buyurtmalar",
            description = "SELLER. companyId majburiy; foydalanuvchi shu faol kompaniyaning egasi bo'lishi kerak. "
                    + "status va sana oralig'i bilan filtrlash mumkin.")
    public ApiResponse<Page<OrderResponse>> seller(@Valid @ParameterObject @ModelAttribute OrderFilter filter) {
        return ApiResponse.successResponse(orderService.getSellerOrders(filter));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Bitta buyurtma tafsiloti",
            description = "Buyurtmani yaratgan xaridor yoki kompaniyaning SELLER egasi ko'radi. Yakunlanmagan holat Control bilan sinxronlanadi.")
    public ApiResponse<OrderResponse> get(
            @Parameter(description = "Order buyurtma ID'si", example = "10")
            @PathVariable("id") @Positive Long orderId
    ) {
        return ApiResponse.successResponse(orderService.get(orderId));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Buyurtmani jo'natish va ombordan chiqim qilish",
            description = "Faqat kompaniya egasi SELLER. Body kerak emas. RESERVED → SHIPPED; rezerv commit qilinadi. "
                    + "Takroriy so'rov qoldiqni qayta kamaytirmaydi. Mos bo'lmagan holat 409.")
    public ApiResponse<OrderResponse> ship(
            @Parameter(description = "Order buyurtma ID'si", example = "10")
            @PathVariable("id") @Positive Long orderId
    ) {
        return ApiResponse.successResponse(orderService.ship(orderId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Buyurtmani bekor qilish va rezervni bo'shatish",
            description = "Xaridor yoki kompaniyaning SELLER egasi. Body kerak emas. Jismoniy qoldiq kamaymaydi. "
                    + "Jo'natilgan yoki jo'natilayotgan buyurtmani bekor qilish 409 qaytaradi.")
    public ApiResponse<OrderResponse> cancel(
            @Parameter(description = "Order buyurtma ID'si", example = "10")
            @PathVariable("id") @Positive Long orderId
    ) {
        return ApiResponse.successResponse(orderService.cancel(orderId));
    }
}
