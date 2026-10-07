# Vakansiya favorites API

BUYER va SELLER rollari uchun. Authorization: Bearer token va Accept-Language: UZ/RU/EN.
Foydalanuvchi ID si tokendagi profileId orqali olinadi; requestda userId berilmaydi.

| Method | URL | Natija |
|---|---|---|
| POST | /api/v1/vacancy-favorites/{vacancyId} | data.favorited: true |
| DELETE | /api/v1/vacancy-favorites/{vacancyId} | data.favorited: false |
| GET | /api/v1/vacancy-favorites?page=1&perPage=20 | data: PageImpl<VacancyDTO> |
| GET | /api/v1/vacancy-favorites/count | data.count |

POST va DELETE uchun body kerak emas. PUT kerak emas: POST o'chirilgan favorite'ni qayta faollashtiradi.
Takror POST yangi yozuv yaratmaydi. DELETE faqat joriy foydalanuvchining yozuvini o'chiradi.
Favorite yo'q bo'lsa DELETE 400 beradi, product favorite API kabi.

Faqat PUBLISHED va deleted=false vakansiya saqlanadi. Ro'yxat va count ham shu shartlarni ishlatadi.
Keyin yopilgan, arxivlangan yoki o'chirilgan vakansiya ro'yxat/countdan yashiriladi, lekin favorite yozuvi saqlanadi.
Bo'sh ro'yxat 200 va content: [] beradi. page 1 dan boshlanadi, perPage 1..100.

Jadval: vacancy_favorites. (user_id, vacancy_id) unique constraint dublikatlarni cheklaydi.
Mavjud local/prod ddl-auto: update konfiguratsiyasi jadvalni yaratadi.

Deploy: sklad-jobs va api-gateway kodini build qilib yangilash kerak.