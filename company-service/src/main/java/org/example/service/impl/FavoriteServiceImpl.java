package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.CompanyResponseDTO;
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
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final ResourceBundleService messageService;
    private final CompanyRepository companyRepository;
    private final org.example.service.CompanyProductCountService productCounts;
    private final ModelMapper modelMapper;


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

    @Override
    public PageImpl<CompanyResponseDTO> getFavorites(int page, int perPage, AppLanguage language) {
        Long profileId = requireProfileId(language);
        Page<Company> companyPage = companyRepository.findFavoriteCompanyDeletedFalse(profileId, PageRequest.of(page-1, perPage));

        List<CompanyResponseDTO> items =companyPage.getContent()
                .stream()
                .map(company -> modelMapper.map(company, CompanyResponseDTO.class))
                .toList();

        productCounts.enrich(items, CompanyResponseDTO::getId, CompanyResponseDTO::setProductCount);
        return new PageImpl<>(items, PageRequest.of(page-1, perPage), companyPage.getTotalElements());
    }


    private Long requireProfileId(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profileId;
    }

}
