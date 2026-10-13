package org.example.ai.jobs;

import java.text.Normalizer;
import java.util.*;

/** Small deterministic multilingual occupation vocabulary; never infers category IDs or skills. */
final class JobText {
    private JobText() {}
    private static final Set<String> STOP = Set.of("job", "jobs", "work", "looking", "find", "for", "the", "and", "a", "an", "need",
            "ish", "kerak", "qidiraman", "ishlari", "ishchi", "ищу", "работ", "работа", "работу", "вакансия", "нужен", "для", "и");
    private static final List<List<String>> GROUPS = List.of(
            List.of("driver", "haydovch", "ҳайдовч", "водител"), List.of("accountant", "buxgalter", "бухгалтер"),
            List.of("warehouse", "ombor", "омбор", "склад"), List.of("developer", "programmer", "dasturch", "дастурч", "программист", "разработчик"),
            List.of("welder", "payvand", "пайванд", "сварщик"), List.of("electrician", "elektrik", "электрик"),
            List.of("engineer", "muhandis", "муҳандис", "инженер"), List.of("cook", "oshpaz", "ошпаз", "повар"),
            List.of("sales", "sotuv", "сотув", "продаж"), List.of("cleaner", "farrosh", "фаррош", "уборщик"),
            List.of("designer", "dizayner", "дизайнер"), List.of("teacher", "oqituvch", "ўқитувч", "учител"));

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("['’‘ʻ`ʼ]", "").replaceAll("[^\\p{L}\\p{N}+#]+", " ").trim();
    }
    static List<String> words(String text) {
        return Arrays.stream(normalize(text).split(" +")).filter(word -> word.length() >= 2 && !STOP.contains(word)).distinct().limit(8).toList();
    }
    static List<String> terms(String title) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(words(title));
        for (var group : GROUPS) if (terms.stream().anyMatch(term -> group.stream().anyMatch(alias -> term.startsWith(alias)))) terms.addAll(group);
        return terms.stream().filter(term -> term.length() <= 60).limit(12).toList();
    }
    static boolean roleMatches(String query, String title) {
        return roleMatches(query, title, "");
    }
    static boolean roleMatches(String query, String title, String publicEvidence) {
        List<String> words = words(query);
        if (words.isEmpty()) return false;
        List<String> target = words(title);
        // At least one occupation term must match the actual title, not just incidental description text.
        if (words.stream().noneMatch(word -> matchesWord(word, target))) return false;
        List<String> evidence = Arrays.stream(normalize(title + " " + publicEvidence).split(" +")).limit(300).toList();
        long matches = words.stream().filter(word -> matchesWord(word, evidence)).count();
        // Every meaningful specialization/technology must have evidence; never drop "forklift" just because "driver" matches.
        return matches == words.size();
    }
    private static boolean matchesWord(String word, List<String> target) {
        if (target.stream().anyMatch(t -> same(word, t))) return true;
        return GROUPS.stream().anyMatch(group -> group.stream().anyMatch(word::startsWith)
                && target.stream().anyMatch(t -> group.stream().anyMatch(t::startsWith)));
    }
    static boolean skillMatches(String skill, String evidence) {
        String normalized = normalize(skill);
        String haystack = " " + normalize(evidence) + " ";
        if (normalized.length() < 2) return false;
        // Token boundaries avoid Java matching JavaScript and C matching every word.
        return haystack.contains(" " + normalized + " ");
    }
    private static boolean same(String a, String b) {
        if (a.equals(b) || a.equals(b + "s") || b.equals(a + "s")) return true;
        // Limited Cyrillic morphology only; never treat Java as JavaScript or unrelated Latin prefixes as roles.
        return Math.min(a.length(), b.length()) >= 5 && a.matches("[\\p{IsCyrillic}]+") && b.matches("[\\p{IsCyrillic}]+")
                && (a.startsWith(b) || b.startsWith(a));
    }
}
