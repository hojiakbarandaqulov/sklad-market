package org.example.service.impl;
import org.example.client.*;
import org.example.client.dto.*;
import org.example.entity.Product;
import org.example.enums.*;
import org.example.enums.Currency;
import org.example.repository.*;
import org.example.service.*;
import org.example.dto.product.CreateProductRequest;
import org.example.exp.AppBadException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ControlProductIntegrationTest {
    @Mock ProductRepository repository;
    @Mock ProductImageRepository images;
    @Mock ProductSearchService search;
    @Mock CompanyClient companies;
    @Mock CategoryClient categories;
    @Mock KafkaProducerService kafka;
    @Mock ViewAsyncService views;
    @Mock ResourceBundleService messages;
    @Mock FileClient files;
    @Mock ControlInventoryClient control;
    @InjectMocks ProductServiceImpl service;
    Product product;
    @BeforeEach void setup() {
        var jwt=Jwt.withTokenValue("seller").header("alg","RS256").claim("profileId",42L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var owner=new CompanyOwnershipResponse();
        owner.setCompanyId(1L); owner.setExists(true); owner.setOwner(true); owner.setActive(true);
        lenient().when(companies.checkOwnership(1L,42L)).thenReturn(owner);
        product=new Product();
        product.setId(100L); product.setCompanyId(1L); product.setSellerId(42L);
        product.setName("Steel"); product.setCurrency(Currency.UZS); product.setPriceType(PriceType.FIXED);
        product.setModerationStatus(ProductModerationStatus.PENDING); product.setSlug("stable-slug");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void linkedProductCannotBeDeletedOrRemovedFromSearch() {
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(control.linked(100L, 1L, "Bearer seller", "UZ")).thenReturn(true);
        assertThatThrownBy(() -> service.delete(100L, AppLanguage.UZ))
                .isInstanceOfSatisfying(org.example.exp.ProductDeletionException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        assertThat(product.getDeletedAt()).isNull();
        verify(repository, never()).save(any());
        verifyNoInteractions(search);
    }

    @Test void unlinkedProductCanBeDeletedButUnknownReferenceStatusFailsClosed() {
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(control.linked(100L, 1L, "Bearer seller", "UZ")).thenReturn(null);
        assertThatThrownBy(() -> service.delete(100L, AppLanguage.UZ))
                .isInstanceOfSatisfying(org.example.exp.ProductDeletionException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        assertThat(product.getDeletedAt()).isNull();
        when(control.linked(100L, 1L, "Bearer seller", "UZ")).thenReturn(false);
        service.delete(100L, AppLanguage.UZ);
        assertThat(product.getDeletedAt()).isNotNull();
        verify(search).delete(100L);
    }

    @Test void inventoryServiceOutageDoesNotDeleteTheProduct() {
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(control.linked(100L, 1L, "Bearer seller", "UZ")).thenThrow(mock(feign.FeignException.class));
        assertThatThrownBy(() -> service.delete(100L, AppLanguage.UZ))
                .isInstanceOfSatisfying(org.example.exp.ProductDeletionException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        verify(repository, never()).save(any());
        verifyNoInteractions(search);
    }
    @Test void retryUsesOriginalCardWithoutPublishingOrCreatingAgain() {
        String slug="control-"+UUID.nameUUIDFromBytes("1:ST-1".getBytes(StandardCharsets.UTF_8));
        when(repository.findBySlugAndDeletedAtIsNull(slug)).thenReturn(Optional.of(product));
        CreateProductRequest request=new CreateProductRequest(); request.setCompanyId(1L);
        assertThat(service.createForControl(request,"ST-1",false,AppLanguage.UZ).getId()).isEqualTo(100L);
        verify(repository,never()).save(any()); verifyNoInteractions(search,kafka,categories);
    }
    @Test void newControlCardStartsHiddenAndOutsideModerationQueue() {
        when(repository.findBySlugAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> { Product p=call.getArgument(0); p.setId(101L); return p; });
        var category=new CategoryValidationResponse(); category.setExists(true); category.setActive(true);
        when(categories.validate(5L)).thenReturn(category);
        var request=request();
        var response=service.createForControl(request,"ST-1",false,AppLanguage.UZ);
        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getStatus()).isEqualTo(ProductModerationStatus.ARCHIVED);
        assertThat(response.getSlug()).startsWith("control-");
    }
    @Test void showingAnArchivedControlProductSubmitsItForModeration() {
        product.setModerationStatus(ProductModerationStatus.ARCHIVED);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result=service.visibilityForControl(100L,true,AppLanguage.UZ);
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ProductModerationStatus.PENDING);
        verify(search).index(argThat(document -> "PENDING".equals(document.getModerationStatus())));
    }

    @Test void hidingRejectedCardPreservesModerationRestriction() {
        product.setModerationStatus(ProductModerationStatus.REJECTED);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(any())).thenReturn(product);
        var hidden = service.visibilityForControl(100L, false, AppLanguage.UZ);
        assertThat(hidden.getStatus()).isEqualTo(ProductModerationStatus.REJECTED);
        assertThatThrownBy(() -> service.visibilityForControl(100L, true, AppLanguage.UZ))
                .isInstanceOf(AppBadException.class);
        verify(repository).saveAndFlush(product);
    }

    @Test void editingBlockedCardPreservesItsBlock() {
        product.setModerationStatus(ProductModerationStatus.APPROVED);
        product.setIsActive(false);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(any())).thenReturn(product);
        var category = new CategoryValidationResponse();
        category.setExists(true);
        category.setActive(true);
        when(categories.validate(5L)).thenReturn(category);
        service.updateForControl(100L, request(), AppLanguage.UZ);
        assertThatThrownBy(() -> service.visibilityForControl(100L, true, AppLanguage.UZ))
                .isInstanceOf(AppBadException.class);
    }
    @Test void controlUpdateCannotMoveCardToAnotherCompany() {
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        var request=request(); request.setCompanyId(2L);
        assertThatThrownBy(() -> service.updateForControl(100L,request,AppLanguage.UZ)).isInstanceOf(AppBadException.class);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void sellerCannotReactivateAdminBlockedApprovedCard() {
        product.setModerationStatus(ProductModerationStatus.APPROVED); product.setIsActive(false);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.visibilityForControl(100L,true,AppLanguage.UZ)).isInstanceOf(AppBadException.class);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void hidingBlockedCardDoesNotEraseItsBlock() {
        product.setModerationStatus(ProductModerationStatus.APPROVED); product.setIsActive(false);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.visibilityForControl(100L,false,AppLanguage.UZ);
        assertThat(product.getModerationStatus()).isEqualTo(ProductModerationStatus.APPROVED);
        assertThatThrownBy(() -> service.visibilityForControl(100L,true,AppLanguage.UZ)).isInstanceOf(AppBadException.class);
    }
    @Test void hidingApprovedCardArchivesIt() {
        product.setModerationStatus(ProductModerationStatus.APPROVED); product.setIsActive(true);
        when(repository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result=service.visibilityForControl(100L,false,AppLanguage.UZ);
        assertThat(result.getStatus()).isEqualTo(ProductModerationStatus.ARCHIVED);
        assertThat(result.getIsActive()).isFalse();
    }
    private CreateProductRequest request() {
        // Testlar faqat mahsulotning amaldagi DTO maydonlarini yuboradi.
        var r=new CreateProductRequest(); r.setCompanyId(1L); r.setCategoryId(5L); r.setName("Steel");
        r.setDescription("Steel sheet"); r.setPriceType(PriceType.FIXED); r.setCurrency(Currency.UZS);
        r.setPrice(BigDecimal.TEN); r.setMinProduct(1L); r.setPhone("99890"); return r;
    }
    @Test void hiddenApprovedCardCannotBeOpenedByPublicSlug() {
        product.setModerationStatus(ProductModerationStatus.APPROVED); product.setIsActive(false);
        when(repository.findBySlugAndModerationStatusAndDeletedAtIsNull("stable-slug",ProductModerationStatus.APPROVED))
            .thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.getPublicDetail("stable-slug",null,AppLanguage.UZ)).isInstanceOf(AppBadException.class);
        verifyNoInteractions(search,views);
    }
}
