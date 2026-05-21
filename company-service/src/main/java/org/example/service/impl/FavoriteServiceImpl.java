package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.favorite.FavoriteCountResponse;
import org.example.entity.Company;
import org.example.entity.Favorite;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.CompanyRepository;
import org.example.repository.FavoriteRepository;
import org.example.service.FavoriteService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final ResourceBundleService messageService;
    private final CompanyRepository companyRepository;


    @Override
    public Boolean createFavorite(Long companyId, AppLanguage language) {
        Long profileId = requireProfileId(language);
        companyRepository.findByIdAndDeletedFalse(companyId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("company.not.found",language)));
        if (favoriteRepository.findByUserIdAndCompanyIdAndDeletedFalse(profileId,companyId).isPresent()){
            throw new AppBadException(messageService.getMessage("company.already.favorite",language));
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(profileId);
        favorite.setCompanyId(companyId);
        favoriteRepository.save(favorite);
        return true;
    }

    @Override
    public FavoriteCountResponse getCount(AppLanguage language) {
        Long profileId = requireProfileId(language);
        Long count=favoriteRepository.countByUserIdAndDeletedFalse(profileId);
        FavoriteCountResponse favoriteCountResponse = new FavoriteCountResponse();
        favoriteCountResponse.setFavoriteCount(count);
        return favoriteCountResponse;
    }

    @Override
    public Boolean remove(Long companyId, AppLanguage language) {
        Long profileId = requireProfileId(language);
        Favorite favorite=favoriteRepository.findByUserIdAndCompanyIdAndDeletedFalse(profileId,companyId)
                .orElseThrow(()->new AppBadException(messageService.getMessage("favorite.not.found",language)));
        favorite.setDeleted(true);
        favoriteRepository.save(favorite);
        return true;
    }


    private Long requireProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profileId;
    }

}
