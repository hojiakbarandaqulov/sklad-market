package org.example.service;

import com.example.dto.ApiResponse;
import com.example.dto.stockOperation.RezervRequest;
import com.example.dto.stockOperation.RezervResponse;
import com.example.dto.stockOperation.AdjustmentRequest;
import com.example.dto.stockOperation.AdjustmentResponse;
import com.example.dto.stockOperation.WriteOffResponse;
import com.example.enums.AppLanguage;
import jakarta.validation.Valid;

public interface StockOperationService {
   AdjustmentResponse adjust(@Valid AdjustmentRequest request, AppLanguage language);
   RezervResponse rezervAdd(@Valid RezervRequest rezervRequest, AppLanguage language);

   WriteOffResponse writeOffs(@Valid RezervRequest rezervRequest, AppLanguage language);
}
