# SKLAD JOBS — API dokumentatsiyasi

Tekshirilgan sana: 2026-10-04. Manba: sklad-jobs controller, DTO, service, repository, security va enum fayllari.
Bu hujjat amaldagi kodni tasvirlaydi; rejalashtirilgan API'lar alohida keltirilgan.
Misollardagi ID va ma'lumotlar namuna. Serverda jonli so'rovlar bajarilmadi.

**Jami: 22 ta API endpoint, 6 ta controller.** Hisob birligi: HTTP method + URL yo'li.
Feign clientlarining boshqa servislarga so'rovlari, chat-service endpointlari, Swagger/Actuator va rejalashtirilgan API'lar bu songa kiritilmagan.

| Controller | API soni |
|---|---:|
| PublicVacancyController | 2 |
| VacancyController | 7 |
| ResumeController | 5 |
| VacancyApplicationController | 1 |
| JobApplicationController | 6 |
| ApplicationChatController | 1 |
| **Jami** | **22** |

Method bo'yicha: **GET 8, POST 10, PUT 2, PATCH 1, DELETE 1**.

2026-09-29 hujjatiga nisbatan: chat ochish endpointi qo'shildi, vakansiya arizalari yo'li ajratildi,
SELLER ruxsatlari va ariza statusini o'zgartirish qoidalari yangilandi, response'ga `chatThreadId` qo'shildi.
Chat sozlamalari va deploy tafsilotlari: [chat integratsiyasi](JOBS_CHAT_AND_ROADMAP_UZ.md).

## 1. Ulanish va umumiy qoidalar

- API base URL: `https://skladmarket.uz` — Nginx API yo'llarini jobs servisiga yo'naltirgan bo'lishi kerak.
- Servis porti: `8093`. Compose'da 127.0.0.1 ga bog'langan, tashqaridan bevosita ochilmasligi normal.
- Prod Swagger yo'li: `/swagger/skladjobs/swagger-ui/index.html` (Nginx mos sozlangan bo'lsa).
- OpenAPI: `/swagger/skladjobs/v3/api-docs`; servisning o'zida `/v3/api-docs`.
- Header: `Accept-Language: UZ` (default), `RU` yoki `EN`. Aynan enum yozilishi ishlatiladi.
- Himoyalangan API: `Authorization: Bearer <access_token>`.
- JSON body yuborilganda: `Content-Type: application/json`.
- Rol nomlari kodda BUYER, SELLER, ADMIN. Alohida HR ruxsati hozir controllerlarda yo'q.
- Query parametri `perPage`, `per_page` emas.
- Request sahifasi `page=1` dan, response `number=0` dan boshlanadi.

### Muvaffaqiyatli javoblar

Obyekt uchun:
```json
{"success":true,"data":{"id":501}}
```

Submit/close/archive/moderation javobi `data` emas, `message` qaytaradi:
```json
{"success":true,"message":"Amal bajarildi"}
```

Pagination javobi (qisqartirilgan):
```json
{
  "success": true,
  "data": {
    "content": [],
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true,
    "empty": true
  }
}
```
PageImpl qo'shimcha `pageable`, `sort`, `numberOfElements` maydonlarini ham chiqarishi mumkin.

## 2. Endpointlar ro'yxati

| № | Rol | Method va yo'l | Vazifa | Muvaffaqiyat javobi |
|---|---|---|---|---|
| 1 | Barcha | GET /api/v1/vacancies | Qidiruv va filtr | 200, Page<PublicVacancyDTO> |
| 2 | Barcha | GET /api/v1/vacancies/{id} | E'lon tafsiloti | 200, PublicVacancyDTO |
| 3 | SELLER | POST /api/v1/vacancy/create | Qoralama yaratish | 200, VacancyDTO |
| 4 | SELLER | POST /api/v1/vacancy/update | Tahrirlash | 200, VacancyDTO |
| 5 | SELLER | GET /api/v1/vacancy/company | Kompaniya vakansiyalari | 200, Page<VacancyDTO> |
| 6 | SELLER | POST /api/v1/vacancy/{vacancyId}/submit | Moderatsiyaga yuborish | 200, message |
| 7 | SELLER | POST /api/v1/vacancy/{vacancyId}/close | Yopish | 200, message |
| 8 | SELLER | POST /api/v1/vacancy/{vacancyId}/archive | Arxivlash | 200, message |
| 9 | ADMIN | POST /api/v1/vacancy/admin/{vacancyId}/moderation | Tasdiqlash/rad etish | 200, message |
| 10 | BUYER | POST /api/v1/me/resumes | Rezyume yaratish | 201, ResumeDTO |
| 11 | BUYER | GET /api/v1/me/resumes | O'z rezyumelari | 200, Page<ResumeDTO> |
| 12 | BUYER | GET /api/v1/me/resumes/{id} | Rezyume tafsiloti | 200, ResumeDTO |
| 13 | BUYER | PUT /api/v1/me/resumes/{id} | Rezyumeni to'liq tahrirlash | 200, ResumeDTO |
| 14 | BUYER | DELETE /api/v1/me/resumes/{id} | Soft delete | 204, body yo'q |
| 15 | BUYER | POST /api/v1/vacancies/{id}/applications | Ariza yuborish | 201, JobApplicationDTO |
| 16 | BUYER | GET /api/v1/me/applications | O'z arizalari | 200, Page<JobApplicationDTO> |
| 17 | BUYER | GET /api/v1/me/applications/{id} | O'z arizasi tafsiloti | 200, JobApplicationDTO |
| 18 | BUYER | POST /api/v1/me/applications/{id}/withdraw | Arizani bekor qilish | 200, JobApplicationDTO |
| 19 | BUYER | PATCH /api/v1/me/applications/{id}/resume | Rezyumeni almashtirish | 200, JobApplicationDTO |
| 20 | SELLER | PUT /api/v1/me/applications/{id}/status | ACCEPTED/REJECTED; kompaniya egasi | 200, JobApplicationDTO |
| 21 | SELLER | GET /api/v1/me/applications/vacancy/{vacancyId} | Vakansiyaning NEW arizalari; kompaniya egasi | 200, Page<JobApplicationDTO> |
| 22 | BUYER / SELLER | POST /api/v1/applications/{id}/chat | Ariza chatini ochish yoki mavjudini olish | 200, ApplicationChatDTO |

22 ta method + URL juftligi noyob. 17 va 21 endpoint yo'llari endi to'qnashmaydi.
Rollar bo'yicha: public 2, faqat SELLER 8, faqat ADMIN 1, faqat BUYER 10, BUYER/SELLER umumiy 1.

## 3. Public vakansiyalar

### 3.1 GET /api/v1/vacancies

Barcha filtrlar ixtiyoriy:

| Parametr | Tip / default | Qoidasi |
|---|---|---|
| q | String | Max 200 belgi; positionName, shortDescription, responsibilities, requirements ichidan qidiradi |
| companyId | Long | Musbat ID |
| regionId | Long | Musbat ID |
| salaryMin | Decimal | >=0; 17 butun, 2 kasr raqamigacha |
| salaryMax | Decimal | >=salaryMin; 17 butun, 2 kasr raqamigacha |
| experienceLevel | Enum | 7-bo'lim |
| employmentType | String | Max 255; registr hisobga olinmaydigan aniq moslik |
| workSchedule | String | Max 255; registr hisobga olinmaydigan aniq moslik |
| page | int, 1 | >=1 |
| perPage | int, 20 | 1..100 |

Namuna:
```http
GET /api/v1/vacancies?q=operator&regionId=1&salaryMin=3000000&page=1&perPage=20
Accept-Language: UZ
```
Faqat `PUBLISHED` va `deleted=false`. Saralash: publishedAt DESC, id DESC.
Kompaniya nomi va logotipi company-service'dan olinadi. Ro'yxatda contacts berilmaydi.
Response: pagination ichida PublicVacancyDTO (6-bo'lim).

### 3.2 GET /api/v1/vacancies/100

100 — vakansiya ID. Faqat e'lon qilingan, o'chirilmagan vakansiya qaytadi.
Noto'g'ri ID: 400. Topilmasa/e'lon qilinmagan bo'lsa: 404.
`showContacts=true` bo'lsa, company-service orqali kontaktlar olinadi; aks holda contacts maydoni yo'q.
Hozir bu GET ko'rishlar sonini oshirmaydi; mavjud viewsCountCache qiymatini qaytaradi.

## 4. SELLER / ADMIN vakansiyalar

### 4.1 POST /api/v1/vacancy/create

```json
{
  "companyId": 42,
  "positionName": "Ombor operatori",
  "price": 5000000,
  "employmentType": "FULL_TIME",
  "workSchedule": "5/2",
  "shortDescription": "Omborga operator kerak",
  "regionId": 1,
  "address": "Toshkent, Yunusobod",
  "lng": "69.28",
  "lat": "41.31",
  "experienceLevel": "ONE_TO_THREE_YEARS",
  "responsibilities": "Mahsulotlarni qabul qilish",
  "requirements": "Omborda ishlash tajribasi",
  "workingConditions": "Rasmiy ish va tushlik",
  "showContacts": true
}
```

Majburiy (`@NotNull`): companyId, positionName, employmentType, workSchedule,
shortDescription, regionId, experienceLevel, responsibilities, requirements, workingConditions.
Qolganlari ixtiyoriy. showContacts default false; null ham false sifatida saqlanadi.
Matn maydonlarida @NotNull bo'sh stringni taqiqlamaydi. price uchun DTO'da maxsus musbatlik cheklovi yo'q.
employmentType va workSchedule erkin String; FULL_TIME faqat namuna, backend enum emas.
Backend JWT profilining kompaniyaga egaligini tekshiradi; boshlang'ich status DRAFT.
Javob: 200 + VacancyDTO. Unda hozir vakansiya `id` maydoni yo'q (8-bo'lim).

### 4.2 POST /api/v1/vacancy/update?vacancyId=100

Body yuqoridagi bilan bir xil, lekin `companyId` yuborilmaydi.
Qisman PATCH emas: majburiy maydonlar to'liq beriladi, ixtiyoriy maydonlar ham yangidan yoziladi.
Tahrirdan keyin status DRAFT bo'ladi. Topilmagan vakansiya: 400.
Muhim: amaldagi metodda kompaniya egasini tekshirish yo'q; 8-bo'limga qarang.

### 4.3 GET /api/v1/vacancy/company?companyId=42&page=1&perPage=20

companyId majburiy; page default 1, perPage default 20.
JWT profilining kompaniyaga egaligi tekshiriladi. O'chirilmagan vakansiyalar, barcha statuslar.
Javob: Page<VacancyDTO>. Bu metodda perPage<=100 chegarasi alohida tekshirilmagan.

### 4.4 Submit / close / archive

Body yo'q:
```http
POST /api/v1/vacancy/100/submit
POST /api/v1/vacancy/100/close
POST /api/v1/vacancy/100/archive
```
Mos natijalar: UNDER_MODERATION, CLOSED, ARCHIVE.
Uchalasida ham seller kompaniya egasi ekanligi tekshiriladi.
Hozir oldingi statusdan o'tish cheklovlari alohida yozilmagan.
Javob: `{"success":true,"message":"..."}`; xabar Accept-Language bo'yicha.

### 4.5 POST /api/v1/vacancy/admin/100/moderation?vacancyModeration=PUBLISHED

ADMIN. Body yo'q. Query `vacancyModeration` majburiy: PUBLISHED yoki REJECTED.
Javob: 200, success/message. Bu endpoint yashirish/bloklash amallarini qo'llamaydi.
Hozir PUBLISHED berilganda publishedAt alohida belgilanmaydi.

## 5. Rezyume, arizalar va chat (BUYER / SELLER)

### 5.1 Rezyume yaratish va tahrirlash

POST /api/v1/me/resumes — 201.
PUT /api/v1/me/resumes/501 — 200; bir xil request:

```json
{
  "title": "Ombor operatori rezyumesi",
  "fullName": "Ali Valiyev",
  "phone": "+998901234567",
  "email": "ali@example.com",
  "regionId": 1,
  "desiredPosition": "Ombor operatori",
  "expectedSalary": 5000000,
  "aboutMe": "Mas'uliyatli xodim",
  "skills": "Excel, ombor hisobi",
  "workExperience": "2023–2026: ombor operatori",
  "education": "O'rta maxsus",
  "languages": "O'zbek, rus"
}
```

Majburiy: title, fullName, phone, email, regionId.
- title/fullName: bo'sh emas, max 255.
- phone: bo'sh emas, max 32; raqamlar, boshidagi +, bo'shliq, (), - qabul qilinadi.
- email: email format, max 255.
- regionId: musbat.
- desiredPosition: max 255.
- expectedSalary: >=0, 17 butun/2 kasr raqami; ixtiyoriy.
- aboutMe/skills/languages: max 10000; workExperience/education: max 20000.
- skills, education va boshqalar JSON array emas, matn.

candidateId JWT'dan olinadi. PUT faqat egasiga tegishli, o'chirilmagan rezyumega ishlaydi.
PUT to'liq yangilaydi: yuborilmagan ixtiyoriy maydonlar null bo'lishi mumkin.
Javob: ResumeDTO; request maydonlari + id, candidateId, createdDate, modifiedDate.

### 5.2 GET /api/v1/me/resumes?page=1&perPage=20

Faqat o'zining deleted=false rezyumelari. page>=1, perPage 1..100.
Saralash: createdDate DESC, id DESC. Javob: Page<ResumeDTO>.

### 5.3 GET /api/v1/me/resumes/501

Javob: ResumeDTO. Begona, o'chirilgan yoki mavjud bo'lmagan rezyume: 404.

### 5.4 DELETE /api/v1/me/resumes/501

Body yo'q. deleted=true qilinadi. 204 No Content, JSON body yo'q.
Arizaga oldin yozilgan resumeSnapshot o'chirilmaydi.

### 5.5 POST /api/v1/vacancies/100/applications

100 — vakansiya ID; resumeId — oldin yaratilgan rezyume ID.

```json
{
  "resumeId": 501,
  "fullName": "Ali Valiyev",
  "phone": "+998901234567",
  "email": "ali@example.com",
  "regionId": 1,
  "expectedSalary": 5000000,
  "coverLetter": "Ushbu lavozimga qiziqyapman.",
  "consentAccepted": true
}
```

Majburiy: resumeId, fullName, phone, email, regionId, consentAccepted=true.
fullName max 255; telefon/email validatsiyasi rezyumedagi kabi.
expectedSalary ixtiyoriy, >=0 (17/2 raqam); coverLetter ixtiyoriy, max 10000.
resumeId musbatligi service'da tekshiriladi; regionId DTO'da faqat @NotNull.

Backend:
1. Nomzod ID'sini JWT'dan oladi.
2. Vakansiyaning PUBLISHED va deleted=false ekanini tekshiradi.
3. Rezyume nomzodniki va deleted=false ekanini tekshiradi.
4. Shu nomzod/shu vakansiyada o'chirilmagan ariza mavjudligini tekshiradi.
5. NEW status, server rozilik vaqti, SKLAD_JOBS manbasi bilan ariza saqlaydi.
6. Rezyume nusxasini resumeSnapshot sifatida yozadi; yangi Resume yaratmaydi.

Takroriy ariza barcha oldingi statuslarda, jumladan WITHDRAWN bo'lsa ham 409.
Parallel create so'rovlari vacancy qatorining pessimistic lock'i bilan ketma-ket tekshiriladi.
7. Shu transaction ichida chatPending=true va chatNextAttemptAt saqlanadi.

201 + JobApplicationDTO. `chatThreadId` dastlab null bo'lishi mumkin: chat fon dispatcher orqali ochiladi.
Chat-service vaqtincha ishlamasa ham ariza saqlanib qoladi. Avtomatik foydalanuvchi xabari/bildirishnoma yuborilmaydi.

### 5.6 GET /api/v1/me/applications?status=NEW&page=1&perPage=20

status ixtiyoriy (ApplicationStatus); berilmasa barcha statuslar.
page>=1, perPage 1..100. Faqat JWT egasining o'chirilmagan arizalari.
Saralash: createdDate DESC, id DESC. Javob: Page<JobApplicationDTO>.

### 5.7 GET /api/v1/me/applications/9001

9001 — ariza ID. Service egasini tekshiradi va JobApplicationDTO qaytaradi.
Begona, o'chirilgan yoki mavjud bo'lmagan ariza: 404. Response'da `chatThreadId` ham bor.

### 5.8 POST /api/v1/me/applications/9001/withdraw

Body yo'q. Faqat o'z arizasi. NEW, REVIEWED, IN_COMMUNICATION, INVITED holatlarida ruxsat.
Status WITHDRAWN bo'ladi, withdrawnAt yoziladi. Takroriy withdraw 200; eski vaqt saqlanadi.
ACCEPTED, REJECTED, ARCHIVED holatlarida 409. Javob: JobApplicationDTO.

### 5.9 PATCH /api/v1/me/applications/9001/resume

```json
{"resumeId":502}
```
resumeId majburiy va musbat. Rezyume shu nomzodniki bo'lishi kerak.
NEW, REVIEWED, IN_COMMUNICATION, INVITED holatlarida ruxsat; terminal holatlarda 409.
resumeId va resumeSnapshot yangilanadi; arizadagi kontaktlar va status o'zgarmaydi.
Keyinchalik asl Resume'ni tahrirlash arizadagi nusxani avtomatik o'zgartirmaydi.
Eski snapshotlar tarixi saqlanmaydi. Javob: JobApplicationDTO.

### 5.10 PUT /api/v1/me/applications/9001/status?applicationResponseStatus=ACCEPTED

SELLER. `applicationResponseStatus` majburiy query: ACCEPTED yoki REJECTED; body yo'q.
Method darajasidagi SELLER ruxsati controllerning umumiy BUYER ruxsatini almashtiradi.
Backend vakansiya kompaniyasi JWT foydalanuvchisiga tegishli ekanini tekshiradi.
Faqat NEW, REVIEWED, IN_COMMUNICATION, INVITED holatlaridan o'tish mumkin.
ACCEPTED, REJECTED, ARCHIVED, WITHDRAWN holatlarida 409, jumladan bir xil terminal statusni qayta yuborganda ham.
Write transaction va pessimistic row lock ishlatiladi; firstResponseAt birinchi javobda yoziladi.
Begona kompaniya uchun 403; yo'q/o'chirilgan ariza uchun 404. Javob: 200, JobApplicationDTO.

### 5.11 GET /api/v1/me/applications/vacancy/100?status=NEW&page=1&perPage=20

SELLER. 100 — vakansiya ID; kompaniya egaligi tekshiriladi.
`status` default NEW; GetNewApplicationStatus hozir faqat NEW qiymatini qo'llaydi.
`page` default 1, >=1; `perPage` default 20, 1..100. Faqat o'chirilmagan NEW arizalar.
Saralash: createdDate DESC. Javob: 200, Page<JobApplicationDTO>.
Begona kompaniya: 403; yo'q/o'chirilgan vakansiya: 404; noto'g'ri pagination: 400.
Bu yo'l BUYER ariza tafsiloti `GET /me/applications/{id}` dan alohida.

### 5.12 POST /api/v1/applications/9001/chat

BUYER yoki SELLER. 9001 — ariza ID. Body yo'q, Bearer token kerak.
Faqat ariza nomzodi yoki vakansiya kompaniyasi egasi foydalanishi mumkin.
Mavjud chat bo'lsa o'sha ID qaytadi; eski arizani ham birinchi marta chatga bog'lash mumkin.

Tayyor chat (HTTP 200):
```json
{"success":true,"data":{"applicationId":9001,"chatThreadId":80,"state":"READY"}}
```

Chat hali ochilmasa, ichki token sozlanmagan bo'lsa yoki chat-service Feign xatosi qaytsa (HTTP 200):
```json
{"success":true,"data":{"applicationId":9001,"chatThreadId":null,"state":"PENDING"}}
```

`READY` — chatThreadId olinganini bildiradi; yangi xabar yuborilganini bildirmaydi.
Begona foydalanuvchi: 403; yo'q/o'chirilgan ariza: 404.
Bu metodda Accept-Language parametri yo'q; service xabarlari inglizcha bo'lishi mumkin.

Avtomatik dispatcher default har 10 soniyada 20 tagacha pending arizani tekshiradi.
Muvaffaqiyatsiz fon urinishidan keyingi urinish kamida 60 soniyaga belgilanadi.
Ichki token bo'lmasa dispatcher ishlamaydi; manual ochish PENDING qaytaradi.
Manual POST fon retry vaqtini kutmasdan qayta urinish qilishi mumkin.

Chat xabarlari chat-service'da saqlanadi. `chatThreadId` bilan mavjud
[Chat API](../chat-service/CHAT_API_UZ.md) ishlatiladi; bu API'lar yuqoridagi 22 taga kirmaydi.
Chat yozishmalari ariza statusini avtomatik o'zgartirmaydi.

## 6. Response maydonlari

### VacancyDTO

companyId: Long; positionName: String; price: Decimal; employmentType: String;
workSchedule: String; shortDescription: String; vacancyStatus: VacancyStatus;
regionId: Long; address/lng/lat: String; experienceLevel: ExperienceLevel;
responsibilities/requirements/workingConditions: String; showContacts: Boolean.

Hozir VacancyDTO'da id, publishedAt va viewsCount yo'q.

### PublicVacancyDTO

VacancyDTO maydonlari va qo'shimcha:
- id: Long — vakansiya ID.
- companyName, companyLogo: String — company-service qiymatlari.
- viewsCount: Long.
- publishedAt: Instant; hozir null kelishi mumkin.
- contacts: {phonePrimary, phoneSecondary, website}; faqat detail'da ruxsat bo'lsa.

### ResumeDTO namunasi

```json
{
  "success": true,
  "data": {
    "id": 501,
    "candidateId": 25,
    "title": "Ombor operatori rezyumesi",
    "fullName": "Ali Valiyev",
    "phone": "+998901234567",
    "email": "ali@example.com",
    "regionId": 1,
    "desiredPosition": "Ombor operatori",
    "expectedSalary": 5000000,
    "aboutMe": "Mas'uliyatli xodim",
    "skills": "Excel, ombor hisobi",
    "workExperience": "3 yil",
    "education": "O'rta maxsus",
    "languages": "O'zbek, rus",
    "createdDate": "2026-09-29T10:00:00",
    "modifiedDate": "2026-09-29T10:00:00"
  }
}
```

### JobApplicationDTO namunasi

```json
{
  "success": true,
  "data": {
    "id": 9001,
    "vacancyId": 100,
    "positionName": "Ombor operatori",
    "companyId": 42,
    "resumeId": 501,
    "chatThreadId": null,
    "status": "NEW",
    "fullName": "Ali Valiyev",
    "phone": "+998901234567",
    "email": "ali@example.com",
    "regionId": 1,
    "expectedSalary": 5000000,
    "coverLetter": "Ushbu lavozimga qiziqyapman.",
    "consentAcceptedAt": "2026-09-29T05:00:00Z",
    "reviewedAt": null,
    "withdrawnAt": null,
    "createdDate": "2026-09-29T10:00:00",
    "modifiedDate": "2026-09-29T10:00:00"
  }
}
```

JobApplicationDTO'dagi `chatThreadId`: Long, chat hali yaratilmagan bo'lsa null.
ApplicationChatDTO: `applicationId` (Long), `chatThreadId` (Long yoki null), `state` (READY yoki PENDING).
DTO resumeSnapshot, assignedHrId, source, firstResponseAt, lastContactAt maydonlarini qaytarmaydi.
Instant vaqtlar UTC/Z bilan; LocalDateTime (createdDate/modifiedDate) timezone offsetsiz.

## 7. Enum qiymatlari

| Enum | Qiymatlar |
|---|---|
| AppLanguage | UZ, RU, EN |
| ExperienceLevel | NO_EXPERIENCE, ONE_TO_THREE_YEARS, THREE_TO_SIX_YEARS, OVER_SIX_YEARS |
| VacancyStatus | DRAFT, UNDER_MODERATION, PUBLISHED, REJECTED, CLOSED, ARCHIVE |
| VacancyModeration | PUBLISHED, REJECTED |
| ApplicationStatus | NEW, REVIEWED, IN_COMMUNICATION, INVITED, ACCEPTED, REJECTED, ARCHIVED, WITHDRAWN |
| ApplicationResponseStatus | ACCEPTED, REJECTED |
| GetNewApplicationStatus | NEW |

ARCHIVE (vakansiya) va ARCHIVED (ariza) yozilishiga e'tibor bering.
Enumning mavjudligi uni o'zgartiradigan API ham tayyor degani emas.

## 8. Kodda aniqlangan cheklovlar va muammolar

1. VacancyServiceImpl.updateVacancy: SELLER roli bor, lekin aynan shu kompaniyaga egalik tekshirilmaydi.
2. VacancyDTO'da id yo'q. Create va seller ro'yxatidan keyingi amallar uchun vakansiya ID olinmaydi.
3. Moderatsiya PUBLISHED qilganda publishedAt yozilmaydi. Public GET viewsCount'ni oshirmaydi.
4. Submit/close/archive/moderation'da oldingi statusga asoslangan o'tish cheklovlari yo'q.
5. Ish beruvchiga arizalar ro'yxati hozir faqat NEW statusini qaytaradi; boshqa statuslar filtri yo'q.
6. Alohida HR a'zoligi, resumeSnapshot tafsiloti va status tarixi API'lari yo'q.
7. Bildirishnoma, PDF/DOC/DOCX rezyume upload/download va seller statistikasi jobs API'lariga ulanmagan.
8. Chat integratsiyasi mavjud, lekin token va chat-service sozlamalariga bog'liq; PENDING holatini frontend hisobga olishi kerak.

Eski ariza GET yo'llari to'qnashuvi va SELLER ariza endpointlaridagi egalik/write transaction muammolari tuzatilgan.

Bu hujjatni yozishda backend kodi o'zgartirilmadi. Muammoli endpointlar tayyor deb ko'rsatilmagan.

## 9. Xatoliklar

| HTTP | Mazmuni |
|---|---|
| 400 | Body/parametr noto'g'ri, rozilik yo'q, ayrim seller kompaniya/vakansiya xatolari |
| 401 | Token/profil yo'q yoki token yaroqsiz |
| 403 | Rol yetarli emas yoki ariza/chat uchun kompaniya egaligi/qatnashuvchi ruxsati yo'q |
| 404 | Public vakansiya yoki o'ziga tegishli resume/ariza topilmadi |
| 409 | Takroriy ariza, yopiq vakansiya, arizaning statusi amal uchun mos emas |
| 500 | Kutilmagan server xatosi; 8-bo'limdagi muammolar ham sabab bo'lishi mumkin |

Controller advice orqali javob namunasi:
```json
{
  "success": false,
  "message": "So'rovni tekshiring",
  "errors": {"fullName":"must not be blank"},
  "trace_id": "namunaviy-trace-id"
}
```
errors oddiy biznes xatosida {} bo'ladi. Security filter yoki Spring standart xatolarining
body formati bundan farq qilishi mumkin. Validatsiya xabarlari hammasi tarjima qilinmagan.

## 10. Frontend ulash tartibi

1. SELLER kompaniyani tanlaydi va vakansiya yaratadi (create response ID muammosi avval tuzatilishi kerak).
2. SELLER submit qiladi, ADMIN PUBLISHED qiladi.
3. BUYER public ro'yxat/detail orqali vakansiyani tanlaydi.
4. BUYER /me/resumes orqali rezyume yaratadi yoki mavjudini tanlaydi.
5. /vacancies/{id}/applications ga resumeId, forma va rozilik yuboradi.
6. BUYER /me/applications dan arizalari va holatlarini ko'radi.
7. Kerak bo'lsa withdraw yoki resume PATCH ishlatiladi.
8. SELLER `/me/applications/vacancy/{vacancyId}` orqali NEW arizalarni oladi va `/{id}/status` orqali ACCEPTED/REJECTED qiladi.
9. BUYER yoki kompaniya egasi `/applications/{id}/chat` orqali chatni oladi; READY bo'lsa chat-service interfeysiga o'tadi.
10. PENDING bo'lsa kutish holatini ko'rsating; keyin ariza/chat ma'lumotini yangilang. Chat xabar yuborish jarayoni alohida.

## 11. Hali tayyor bo'lmagan / tavsiya etilgan API'lar
