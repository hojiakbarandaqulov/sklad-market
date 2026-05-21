package org.example.service;

import org.example.dto.favorite.FavoriteCountResponse;
import org.example.enums.AppLanguage;

public interface FavoriteService {
    Boolean createFavorite(Long companyId, AppLanguage language);

    FavoriteCountResponse getCount(AppLanguage language);

    Boolean remove(Long companyId, AppLanguage language);
}
