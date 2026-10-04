/*
package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Category;
import org.example.repository.CategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryDemoSeedRunner implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedCategory> roots = demoTree();
        Map<String, Category> existingBySlug = categoryRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Category::getSlug, Function.identity(), (left, right) -> left));

        int[] counter = {0};
        int sort = 1;
        for (SeedCategory root : roots) {
            upsert(root, null, sort++, existingBySlug, counter);
        }

        log.info("Demo category tree ensured successfully. Upserted/updated {} categories", counter[0]);
    }

    private void upsert(SeedCategory seed,
                        Category parent,
                        int sortOrder,
                        Map<String, Category> existingBySlug,
                        int[] counter) {
        Category category = existingBySlug.getOrDefault(seed.slug(), new Category());
        category.setParent(parent);
        category.setNameUz(seed.nameUz());
        category.setNameRu(seed.nameRu());
        category.setNameEn(seed.nameEn());
        category.setSlug(seed.slug());
        category.setSortOrder(sortOrder);
        category.setIsActive(Boolean.TRUE);

        Category saved = categoryRepository.save(category);
        existingBySlug.put(saved.getSlug(), saved);
        counter[0]++;

        int childSort = 1;
        for (SeedCategory child : seed.children()) {
            upsert(child, saved, childSort++, existingBySlug, counter);
        }
    }

    private List<SeedCategory> demoTree() {
        return List.of(
                new SeedCategory(
                        "Qurilish materiallari",
                        "Строительные материалы",
                        "Construction Materials",
                        "construction-materials",
                        List.of(
                                leaf("Tsement va quruq aralashmalar", "Цемент и сухие смеси", "Cement & Dry Mixes", "cement-dry-mixes"),
                                leaf("G'isht va bloklar", "Кирпич и блоки", "Bricks & Blocks", "bricks-blocks"),
                                new SeedCategory(
                                        "Metall mahsulotlar",
                                        "Металлопрокат",
                                        "Metal Products",
                                        "metal-products",
                                        List.of(
                                                leaf("Armatura", "Арматура", "Rebar", "rebar"),
                                                leaf("Quvurlar", "Трубы", "Pipes", "pipes"),
                                                leaf("List metall", "Листовой металл", "Sheet Metal", "sheet-metal")
                                        )
                                ),
                                leaf("Yog'och materiallari", "Пиломатериалы", "Timber & Lumber", "timber-lumber"),
                                leaf("Tom yopish materiallari", "Кровельные материалы", "Roofing Materials", "roofing-materials")
                        )
                ),
                new SeedCategory(
                        "Pardozlash materiallari",
                        "Отделочные материалы",
                        "Finishing Materials",
                        "finishing-materials",
                        List.of(
                                leaf("Bo'yoq va qoplamalar", "Краски и покрытия", "Paint & Coatings", "paint-coatings"),
                                leaf("Plitka va keramika", "Плитка и керамика", "Tiles & Ceramics", "tiles-ceramics"),
                                leaf("Pol qoplamalari", "Напольные покрытия", "Flooring", "flooring"),
                                leaf("Gipsokarton va ship", "Гипсокартон и потолки", "Drywall & Ceiling", "drywall-ceiling")
                        )
                ),
                new SeedCategory(
                        "Elektr jihozlari",
                        "Электрика",
                        "Electrical Equipment",
                        "electrical-equipment",
                        List.of(
                                leaf("Kabel va simlar", "Кабель и провода", "Cables & Wires", "cables-wires"),
                                leaf("Avtomatlar va щitlar", "Автоматы и щиты", "Circuit Breakers & Panels", "breakers-panels"),
                                leaf("Yoritish", "Освещение", "Lighting", "lighting"),
                                leaf("Rozetka va kalitlar", "Розетки и выключатели", "Sockets & Switches", "sockets-switches")
                        )
                ),
                new SeedCategory(
                        "Santexnika va isitish",
                        "Сантехника и отопление",
                        "Plumbing & Heating",
                        "plumbing-heating",
                        List.of(
                                leaf("Truba va fitinglar", "Трубы и фитинги", "Pipes & Fittings", "pipes-fittings"),
                                leaf("Qozon va radiatorlar", "Котлы и радиаторы", "Boilers & Radiators", "boilers-radiators"),
                                leaf("Nasoslar", "Насосы", "Pumps", "pumps"),
                                leaf("Sanfayans", "Санфаянс", "Sanitary Ware", "sanitary-ware")
                        )
                ),
                new SeedCategory(
                        "Ventilyatsiya va konditsioner",
                        "Вентиляция и кондиционирование",
                        "HVAC & Ventilation",
                        "hvac-ventilation",
                        List.of(
                                leaf("Konditsionerlar", "Кондиционеры", "Air Conditioners", "air-conditioners"),
                                leaf("Ventkanallar", "Воздуховоды", "Air Ducts", "air-ducts"),
                                leaf("Ventilyatorlar", "Вентиляторы", "Fans", "fans")
                        )
                ),
                new SeedCategory(
                        "Asbob-uskunalar",
                        "Инструменты и оборудование",
                        "Tools & Equipment",
                        "tools-equipment",
                        List.of(
                                leaf("Elektr asboblar", "Электроинструменты", "Power Tools", "power-tools"),
                                leaf("Qo'l asboblari", "Ручные инструменты", "Hand Tools", "hand-tools"),
                                leaf("Payvandlash uskunalari", "Сварочное оборудование", "Welding Equipment", "welding-equipment"),
                                leaf("Kompressorlar", "Компрессоры", "Compressors", "compressors")
                        )
                ),
                new SeedCategory(
                        "Ombor va logistika",
                        "Склад и логистика",
                        "Warehouse & Logistics",
                        "warehouse-logistics",
                        List.of(
                                leaf("Stellajlar", "Стеллажи", "Shelving", "shelving"),
                                leaf("Aravachalar", "Тележки", "Trolleys & Carts", "trolleys-carts"),
                                leaf("Qadoqlash materiallari", "Упаковочные материалы", "Packaging Materials", "packaging-materials")
                        )
                ),
                new SeedCategory(
                        "Xomashyo va ishlab chiqarish",
                        "Сырье и производство",
                        "Raw Materials & Manufacturing",
                        "raw-materials-manufacturing",
                        List.of(
                                leaf("Polimer xomashyo", "Полимерное сырье", "Polymer Raw Materials", "polymer-raw-materials"),
                                leaf("Kimyoviy xomashyo", "Химическое сырье", "Chemical Raw Materials", "chemical-raw-materials"),
                                leaf("Sanoat gazlari", "Промышленные газы", "Industrial Gases", "industrial-gases")
                        )
                )
        );
    }

    private SeedCategory leaf(String nameUz, String nameRu, String nameEn, String slug) {
        return new SeedCategory(nameUz, nameRu, nameEn, slug, List.of());
    }

    private record SeedCategory(
            String nameUz,
            String nameRu,
            String nameEn,
            String slug,
            List<SeedCategory> children
    ) {
    }
}
*/
