package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.entity.ProductView;
import org.example.repository.ProductRepository;
import org.example.repository.ProductViewRepository;
import org.example.service.ViewAsyncService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ViewAsyncServiceImpl implements ViewAsyncService {

    private final ProductRepository productRepository;
    private final ProductViewRepository productViewRepository;

    @Override
    public void logView(Long productId, Long userId, String sessionId) {
        ProductView view = new ProductView();
        view.setProductId(productId);
        view.setUserId(userId);
        view.setSessionId(sessionId);
        view.setViewedAt(LocalDateTime.now());
        productViewRepository.save(view);

        productRepository.findById(productId).ifPresent(product -> {
            long current = product.getViewsCountCache() == null ? 0L : product.getViewsCountCache();
            product.setViewsCountCache(current + 1);
            productRepository.save(product);
        });
    }
}
