package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.stockOperation.RezervRequest;
import com.example.dto.stockOperation.RezervResponse;
import com.example.dto.stockOperation.AdjustmentRequest;
import com.example.dto.stockOperation.AdjustmentResponse;
import com.example.dto.stockOperation.WriteOffResponse;
import com.example.enums.AppLanguage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.service.StockOperationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/control/operations")
@PreAuthorize("hasRole('SELLER')")
public class StockOperation {

    private final StockOperationService stockOperationService;

    // Sanash natijasidagi qoldiqni o'rnatadi va farqni tarixga yozadi.
    @PostMapping("/adjustments")
    public ApiResponse<AdjustmentResponse> adjust(@RequestBody @Valid AdjustmentRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(stockOperationService.adjust(request, language));
    }

    @PostMapping("receipts")
    public ApiResponse<RezervResponse> rezervAdd(@RequestBody @Valid RezervRequest rezervRequest,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language){
        RezervResponse rezervResponse=  stockOperationService.rezervAdd(rezervRequest,language);
        return ApiResponse.successResponse(rezervResponse);
    }


    @PostMapping("write-offs")
    public ApiResponse<WriteOffResponse> writeOffs(@RequestBody @Valid RezervRequest rezervRequest,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language){
        WriteOffResponse response = stockOperationService.writeOffs(rezervRequest, language);
        return ApiResponse.successResponse(response);
    }
}
