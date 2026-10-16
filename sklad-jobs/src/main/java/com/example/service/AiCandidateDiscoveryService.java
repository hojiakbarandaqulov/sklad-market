package com.example.service;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.*;
import com.example.enums.ExperienceLevel;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.*;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Bounded, consent-aware discovery. No contacts or raw resume bodies leave this boundary. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AiCandidateDiscoveryService {
    private final ResumeRepository resumes;
    private final ResumeDiscoveryRepository discoveries;
    private final VacancyRepository vacancies;
    private final JobsVacancyAccess access;

    public record Discovery(boolean enabled, String specialty, ExperienceLevel experienceLevel,
            Long regionId, List<String> skills) {}
    public record MatchingProfile(Long resumeId, String desiredPosition, List<String> skills,
            Long regionId, ExperienceLevel experienceLevel, BigDecimal salaryMin) {}
    public record Candidate(Long id, String specialty, ExperienceLevel experienceLevel, Long regionId,
            List<String> skills, List<String> reasons) {}
    public record CandidateDetail(Long id, String specialty, ExperienceLevel experienceLevel, Long regionId,
            List<String> skills,List<String> reasons,String workExperience,String education,String languages) {}
    public record CandidatePage(List<Candidate> items, int page, int perPage, boolean hasMore, Long vacancyId) {}
    public record OwnedVacancy(Long id, String positionName, Long regionId, ExperienceLevel experienceLevel) {}

    public Discovery discovery(Long resumeId) {
        ownedResume(resumeId);
        return discoveries.findById(resumeId).map(this::discoveryDTO)
                .orElse(new Discovery(false, null, null, null, List.of()));
    }

    @Transactional
    public Discovery saveDiscovery(Long resumeId, AiJobsRequests.Discovery request) {
        // Lock the stable resume row even before a discovery setting exists, serializing first opt-in.
        long viewer = access.viewer();
        if (resumeId == null || resumeId <= 0) throw notFound();
        Resume resume = resumes.findOwnedForDiscovery(resumeId, viewer).orElseThrow(AiCandidateDiscoveryService::notFound);
        ResumeDiscovery setting = discoveries.findLocked(resumeId).orElseGet(ResumeDiscovery::new);
        setting.setResume(resume);
        if (Boolean.TRUE.equals(request.enabled())) {
            String specialty = request.specialty() == null ? "" : request.specialty().trim();
            List<String> skills = safeSkills(request.skills());
            if (!Boolean.TRUE.equals(request.consentAccepted()) || specialty.isBlank() || specialty.length() > 120 || unsafeProfessionalText(specialty)
                    || request.experienceLevel() == null || request.regionId() == null
                    || !request.regionId().equals(resume.getRegionId()) || skills.isEmpty())
                throw new AppBadException("Confirm specialty, experience, skills, resume location and discovery consent");
            setting.setSpecialty(specialty); setting.setSkills(String.join("\n", skills));
            setting.setExperienceLevel(request.experienceLevel()); setting.setRegionId(request.regionId());
            setting.setConsentedAt(java.time.Instant.now()); setting.setConsentVersion("2026-10-v1");
        }
        setting.setEnabled(Boolean.TRUE.equals(request.enabled()));
        return discoveryDTO(discoveries.save(setting));
    }

    public MatchingProfile matchingProfile(Long resumeId) {
        Resume resume = ownedResume(resumeId);
        var setting = discoveries.findById(resumeId).orElse(null);
        // Experience is never guessed from prose. Private matching does not require discovery opt-in.
        String position=resume.getDesiredPosition()==null?null:resume.getDesiredPosition().trim();
        if(position!=null && (position.length()>120 || unsafeProfessionalText(position))) position=null;
        return new MatchingProfile(resumeId, position,
                matchingSkills(resume.getSkills()), resume.getRegionId(),
                setting == null ? null : setting.getExperienceLevel(), resume.getExpectedSalary());
    }

    public Map<String, Object> ownedVacancies() {
        long viewer = access.viewer();
        List<Long> companyIds = access.ownedCompanies(viewer);
        Specification<Vacancy> spec = (root, query, cb) -> cb.and(cb.isFalse(root.get("deleted")),
                cb.equal(root.get("vacancyStatus"), VacancyStatus.PUBLISHED),
                cb.or(cb.equal(root.get("buyerId"), viewer), companyIds.isEmpty() ? cb.disjunction() : root.get("companyId").in(companyIds)));
        var result = vacancies.findAll(spec, PageRequest.of(0,100,Sort.by(Sort.Direction.DESC,"id")));
        return Map.of("items", result.getContent().stream().map(v -> new OwnedVacancy(v.getId(),
                bounded(v.getPositionName(),200),v.getRegionId(),v.getExperienceLevel())).toList(), "hasMore",result.hasNext());
    }

    public CandidatePage candidates(Long vacancyId, String q, int page, int perPage) {
        bounds(page,perPage);
        Vacancy vacancy = access.owned(vacancyId,false,true);
        if (q != null && q.length()>200) throw new AppBadException("Search is too long");
        List<String> queryTerms = terms(q);
        if (q != null && !q.isBlank() && queryTerms.isEmpty()) throw new AppBadException("Enter a meaningful professional search");
        List<String> roleTerms = terms(vacancy.getPositionName());
        if (roleTerms.isEmpty()) return new CandidatePage(List.of(),page,perPage,false,vacancyId);
        var source = discoveries.findAll(eligible(vacancy,access.viewer(),roleTerms,queryTerms),
                PageRequest.of(page-1,perPage));
        return new CandidatePage(source.getContent().stream().map(d -> candidateDTO(d,vacancy)).toList(),
                page,perPage,source.hasNext(),vacancyId);
    }

    public CandidateDetail candidate(Long vacancyId, Long resumeId) {
        Vacancy vacancy = access.owned(vacancyId,false,true);
        if (resumeId == null || resumeId <= 0) throw notFound();
        var found = discoveries.findOne(eligible(vacancy,access.viewer(),terms(vacancy.getPositionName()),List.of())
                .and((root,query,cb) -> cb.equal(root.get("resumeId"),resumeId))).orElseThrow(AiCandidateDiscoveryService::notFound);
        Candidate summary=candidateDTO(found,vacancy);
        Resume resume=found.getResume();
        return new CandidateDetail(summary.id(),summary.specialty(),summary.experienceLevel(),summary.regionId(),
                summary.skills(),summary.reasons(),professionalText(resume.getWorkExperience(),resume),
                professionalText(resume.getEducation(),resume),professionalText(resume.getLanguages(),resume));
    }

    /** Send/accept paths recheck current consent under lock, not a stale AI card. */
    public ResumeDiscovery requireEligibleLocked(Vacancy vacancy, Long resumeId, long inviterId) {
        ResumeDiscovery d = discoveries.findLocked(resumeId).orElseThrow(AiCandidateDiscoveryService::notFound);
        if (!d.isEnabled() || Boolean.TRUE.equals(d.getResume().getDeleted())
                || vacancy.getRegionId() == null
                || Long.valueOf(inviterId).equals(d.getResume().getCandidateId())
                || !Objects.equals(vacancy.getRegionId(),d.getRegionId())
                || !Objects.equals(d.getRegionId(),d.getResume().getRegionId())
                || !experienceMatches(vacancy.getExperienceLevel(),d.getExperienceLevel())
                || !roleMatches(vacancy.getPositionName(), d.getSpecialty())) throw notFound();
        return d;
    }

    private Specification<ResumeDiscovery> eligible(Vacancy vacancy, long viewer, List<String> roles,List<String> queryTerms) {
        return (root,query,cb) -> {
            if (vacancy.getRegionId() == null || roles.isEmpty()) return cb.disjunction();
            var predicates=eligibility(root,cb,vacancy,viewer,roles,queryTerms);
            List<String> skillTerms=terms(bounded(vacancy.getRequirements(),2000));
            Expression<Integer> score=skillScore(root,cb,skillTerms);
            Expression<Integer> level=experienceRank(root,cb);
            // Choose one best ELIGIBLE professional resume per person; unrelated newer resumes
            // must neither hide a relevant older resume nor crowd out other candidates.
            var better=query.subquery(Long.class);
            var other=better.from(ResumeDiscovery.class);
            var otherPredicates=eligibility(other,cb,vacancy,viewer,roles,queryTerms);
            Expression<Integer> otherScore=skillScore(other,cb,skillTerms);
            Expression<Integer> otherLevel=experienceRank(other,cb);
            otherPredicates.add(cb.equal(other.get("resume").get("candidateId"),root.get("resume").get("candidateId")));
            otherPredicates.add(cb.or(cb.greaterThan(otherScore,score),
                    cb.and(cb.equal(otherScore,score),cb.lessThan(otherLevel,level)),
                    cb.and(cb.equal(otherScore,score),cb.equal(otherLevel,level),cb.greaterThan(other.get("resumeId"),root.get("resumeId")))));
            better.select(other.get("resumeId")).where(otherPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            predicates.add(cb.not(cb.exists(better)));
            if(query.getResultType()!=Long.class && query.getResultType()!=long.class) {
                if(skillTerms.isEmpty()) query.orderBy(cb.asc(level),cb.desc(root.get("resumeId")));
                else query.orderBy(cb.desc(score),cb.asc(level),cb.desc(root.get("resumeId")));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private List<jakarta.persistence.criteria.Predicate> eligibility(jakarta.persistence.criteria.From<?,ResumeDiscovery> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,Vacancy vacancy,long viewer,List<String> roles,List<String> queryTerms) {
        var resume=root.join("resume");
        List<jakarta.persistence.criteria.Predicate> predicates=new ArrayList<>();
        predicates.add(cb.isTrue(root.get("enabled"))); predicates.add(cb.isFalse(resume.get("deleted")));
        predicates.add(cb.notEqual(resume.get("candidateId"),viewer));
        predicates.add(cb.equal(root.get("regionId"),vacancy.getRegionId()));
        predicates.add(cb.equal(root.get("regionId"),resume.get("regionId")));
        predicates.add(root.get("experienceLevel").in(Arrays.stream(ExperienceLevel.values())
                .filter(e -> experienceMatches(vacancy.getExperienceLevel(),e)).toList()));
        Expression<String> specialty=cb.concat(cb.concat(" ",normalized(root.get("specialty"),cb))," ");
        for(String term:roles) predicates.add(wordPredicate(specialty,cb,term));
        if(roles.contains("react") && !roles.contains("native")) predicates.add(cb.notLike(specialty,"% native %"));
        Expression<String> professional=cb.concat(cb.concat(specialty,normalized(root.get("skills"),cb))," ");
        for(String term:queryTerms) predicates.add(wordPredicate(professional,cb,term));
        return predicates;
    }
    private static jakarta.persistence.criteria.Predicate wordPredicate(Expression<String> text,
            jakarta.persistence.criteria.CriteriaBuilder cb,String term) {
        return cb.or(aliases(term).stream().map(t -> cb.like(text,"% "+escape(t)+" %",'!'))
                .toArray(jakarta.persistence.criteria.Predicate[]::new));
    }
    private static Expression<Integer> skillScore(jakarta.persistence.criteria.From<?,ResumeDiscovery> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,List<String> skillTerms) {
        Expression<Integer> score=cb.literal(0);
        Expression<String> skills=cb.concat(cb.concat(" ",normalized(root.get("skills"),cb))," ");
        for(String term:skillTerms) score=cb.sum(score,cb.<Integer>selectCase().when(wordPredicate(skills,cb,term),1).otherwise(0));
        return score;
    }
    private static Expression<Integer> experienceRank(jakarta.persistence.criteria.From<?,ResumeDiscovery> root,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        var level=cb.<ExperienceLevel,Integer>selectCase(root.get("experienceLevel"));
        for(var value:ExperienceLevel.values()) level.when(value,value.ordinal());
        return level.otherwise(4);
    }

    private Candidate candidateDTO(ResumeDiscovery d,Vacancy vacancy) {
        List<String> skills = splitSkills(d.getSkills());
        List<String> reasons = new ArrayList<>(List.of("ROLE_MATCH","REGION_MATCH","EXPERIENCE_MATCH"));
        String requirements = " " + normalize(vacancy.getRequirements()) + " ";
        if (skills.stream().anyMatch(s -> requirements.contains(" "+normalize(s)+" "))) reasons.add("SKILL_MATCH");
        return new Candidate(d.getResumeId(),d.getSpecialty(),d.getExperienceLevel(),d.getRegionId(),skills,reasons);
    }
    private Resume ownedResume(Long id) {
        long viewer = access.viewer();
        if (id == null || id <= 0) throw notFound();
        return resumes.findByIdAndCandidateIdAndDeletedFalse(id,viewer).orElseThrow(AiCandidateDiscoveryService::notFound);
    }
    private Discovery discoveryDTO(ResumeDiscovery d) {
        return new Discovery(d.isEnabled(),d.getSpecialty(),d.getExperienceLevel(),d.getRegionId(),splitSkills(d.getSkills()));
    }
    public static void bounds(int page,int perPage) {
        if (page<1 || page>100 || perPage<1 || perPage>20) throw new AppBadException("Invalid pagination");
    }
    static boolean experienceMatches(ExperienceLevel required, ExperienceLevel actual) {
        return actual != null && (required == null || actual.ordinal() >= required.ordinal());
    }
    static boolean roleMatches(String role, String specialty) {
        List<String> required = terms(role); String actual = " " + normalize(specialty) + " ";
        return !required.isEmpty() && !(required.contains("react") && !required.contains("native") && actual.contains(" native "))
                && required.stream().allMatch(t -> aliases(t).stream().anyMatch(alias -> actual.contains(" "+alias+" ")));
    }
    private static final Set<String> STOP = Set.of("a","an","the","for","and","with","senior","junior","middle","specialist","специалист","старший","младший","нужен","требуется","kerak","mutaxassis","ish","job");
    private static final List<List<String>> ALIASES = List.of(
            List.of("developer","dasturchi","разработчик","программист"), List.of("driver","haydovchi","водитель"),
            List.of("accountant","buxgalter","бухгалтер"),List.of("welder","payvandchi","сварщик"),
            List.of("teacher","oqituvchi","учитель"),List.of("electrician","elektrik","электрик"),
            List.of("manager","menejer","менеджер"));
    private static List<String> aliases(String term) {
        return ALIASES.stream().filter(group -> group.contains(term)).findFirst().orElse(List.of(term));
    }
    private static List<String> terms(String value) {
        return Arrays.stream(normalize(value).split("[^\\p{L}\\p{N}+#.]+"))
                .filter(t -> (t.length()>=2 || t.equals("c") || t.equals("r")) && !STOP.contains(t)).distinct().limit(12).toList();
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("['’‘ʻ`ʼ]","")
                .replaceAll("[^\\p{L}\\p{N}+#.]+"," ").trim().replaceAll(" +"," ");
    }
    private static Expression<String> normalized(Expression<String> expression,jakarta.persistence.criteria.CriteriaBuilder cb) {
        Expression<String> value = cb.lower(cb.coalesce(expression,""));
        for(String apostrophe:List.of("'","’","‘","ʻ","`","ʼ")) value=cb.function("replace",String.class,value,cb.literal(apostrophe),cb.literal(""));
        for(String separator:List.of("-","/",",",";",":","(",")","\n","\r","\t")) value=cb.function("replace",String.class,value,cb.literal(separator),cb.literal(" "));
        return value;
    }
    private static String escape(String text) { return text.replace("!","!!").replace("%","!%").replace("_","!_"); }
    private static List<String> safeSkills(List<String> skills) {
        if (skills == null) return List.of();
        if (skills.size()>20 || skills.stream().anyMatch(s -> s==null || s.isBlank() || s.trim().length()>60 || unsafeProfessionalText(s)))
            throw new AppBadException("Invalid skills");
        return skills.stream().map(String::trim).distinct().toList();
    }
    private static List<String> splitSkills(String skills) {
        if (skills == null) return List.of();
        return Arrays.stream(skills.split("[,;\\n\\r]+"))
                .map(String::trim).filter(s -> !s.isEmpty() && s.length()<=60).distinct().limit(20).toList();
    }
    private static List<String> matchingSkills(String skills) {
        if(skills==null || skills.length()>2000) return List.of();
        List<String> parsed=Arrays.stream(skills.split("[,;\\n\\r]+"))
                .map(String::trim).filter(s->!s.isEmpty()).distinct().toList();
        // Incomplete/oversized legacy fields require owner confirmation, never silent truncation.
        if(parsed.size()>20 || parsed.stream().anyMatch(s->s.length()>60 || unsafeProfessionalText(s))) return List.of();
        return parsed;
    }
    private static String bounded(String text,int length) {return text==null?null:text.substring(0,Math.min(length,text.length()));}
    private static boolean unsafeProfessionalText(String text) {
        return text.matches("(?s).*[\\p{Cntrl}\\p{Cf}@].*")
                || java.util.regex.Pattern.compile("(?i)https?://|www\\.|(?:\\+?\\d[ ()\\-]*){7,}").matcher(text).find();
    }
    private static String professionalText(String text,Resume resume) {
        if(text==null || text.isBlank()) return null;
        String safe=bounded(text,6000);
        for(String contact:new String[]{resume.getFullName(),resume.getPhone(),resume.getEmail(),resume.getFileId()})
            if(contact!=null && !contact.isBlank()) safe=safe.replaceAll("(?iu)"+java.util.regex.Pattern.quote(contact),"[redacted]");
        safe=safe.replaceAll("(?i)(?:https?://|www\\.)\\S+","[redacted]")
                .replaceAll("[^\\s@]+@[^\\s@]+","[redacted]")
                .replaceAll("(?<![\\p{L}\\p{N}])@[\\p{L}\\p{N}_.]+","[redacted]")
                .replaceAll("(?:\\+?\\d[ ()\\-]*){7,}","[redacted]")
                .replaceAll("[\\p{Cf}\\p{Cntrl}&&[^\\n\\t]]","").trim();
        return safe.isBlank()?null:bounded(safe,2000);
    }
    private static AppNotFoundException notFound() {return new AppNotFoundException("Resume not found");}
}
