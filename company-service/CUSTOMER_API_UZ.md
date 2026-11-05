# Mijozlar va shartnomalar API

API `company-service` ichida. Gateway `/api/v1/customers` va uning ichki yo'llarini shu xizmatga yuboradi.
`Authorization: Bearer <token>` va `SELLER` roli talab qilinadi. Har bir amalda kompaniya egasi tekshiriladi.

| Method | Endpoint | Vazifa |
|---|---|---|
| POST | `/api/v1/customers` | Mijoz yaratish, ixtiyoriy shartnoma fayli bilan |
| GET | `/api/v1/customers` | Mijozlar ro'yxati va statistika |
| GET | `/api/v1/customers/{id}` | Mijoz tafsilotlari |
| PATCH | `/api/v1/customers/{id}` | Mijoz ma'lumotlarini tahrirlash |
| DELETE | `/api/v1/customers/{id}` | Mijozni arxivlash |
| POST | `/api/v1/customers/{id}/contract` | Shartnomani yuklash yoki almashtirish |
| GET | `/api/v1/customers/{id}/contract` | Shartnomani fayl sifatida olish |

## Mijoz yaratish

`POST /api/v1/customers`, `Content-Type: multipart/form-data`.

| Form maydoni | Majburiy | Cheklov |
|---|---|---|
| companyId | ha | Musbat kompaniya ID |
| name | ha | Bo'sh bo'lmasin, 255 belgigacha |
| contractNumber | ha | Bo'sh bo'lmasin, 100 belgigacha |
| phone | yo'q | 30 belgigacha, telefon raqami |
| paymentTermDays | ha | 1–3650 kun |
| file | yo'q | PDF, DOC, DOCX, JPG/JPEG, PNG, WebP, 2 MiB gacha |

`Accept-Language` UZ (standart), RU yoki EN. Upload shu til bilan mavjud
`POST /api/v1/attach/upload` endpointiga `FileClient` orqali yuboriladi.
Fayl tarkibi va kengaytmasi `file-service` tomonidan tekshiriladi.

201 javob namunasi (faylsiz yaratish):

```json
{
  "success": true,
  "data": {
    "id": 101,
    "companyId": 7,
    "name": "Baraka Qurilish",
    "contractNumber": "SH-2026-015",
    "phone": "+998901234567",
    "paymentTermDays": 15,
    "contractFile": null,
    "createdAt": "2026-11-05T15:00:00",
    "updatedAt": "2026-11-05T15:00:00"
  }
}
```

Fayl bo'lsa `contractFile` quyidagicha qaytadi:

```json
{
  "fileName": "shartnoma.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 245760,
  "downloadUrl": "/api/v1/customers/101/contract"
}
```

## Ro'yxat

`GET /api/v1/customers?companyId=7&search=Baraka&hasContractFile=true&page=1&perPage=20`

`companyId` majburiy. Qolgan filtrlar ixtiyoriy. `page` 1 dan, `perPage` 1–100.
Qidiruv mijoz nomi, shartnoma raqami va telefon bo'yicha. Arxivlangan mijozlar chiqarilmaydi.
`hasContractFile=false` faylsiz mijozlarni chiqaradi. Tartib yangi yaratilgan mijozdan boshlanadi.

`data.items` ichida mijoz obyektlari, `data.meta` ichida
`total`, `page`, `perPage`, `totalPages` qaytadi.
`data.summary` barcha filtrlangan natijalar bo'yicha `customersCount`,
`withContractFileCount`, `averagePaymentTermDays` qaytaradi.
Bo'sh natijada statistika qiymatlari 0 bo'ladi.

## Tahrirlash va arxivlash

`PATCH /api/v1/customers/101`, `Content-Type: application/json`:

```json
{"phone":"+998909876543","paymentTermDays":30}
```

`name`, `contractNumber`, `phone`, `paymentTermDays` dan keraklilarini yuboring.
Omitted yoki null maydon o'zgarmaydi. `phone: ""` telefonni tozalaydi.
Javob `200`, `data` ichida yangilangan mijoz.
Faylni almashtirish uchun `POST /api/v1/customers/101/contract` ga multipart `file` yuboring.
Yangi fayl saqlangandan keyin eski fayl tozalanadi. Bazadagi amal rollback bo'lsa yangi fayl tozalanadi.
Masofaviy tozalash xatosi server logida qayd etiladi.

`DELETE /api/v1/customers/101` takroriy yuborilishi mumkin. Mijoz va shartnoma bazada saqlanadi.

```json
{"success":true,"data":{"id":101,"archived":true}}
```

## Xatolar va ishga tushirish

Customer API xatolari `success: false`, `message`, `errors`, `trace_id` bilan qaytadi.
400 — noto'g'ri maydon yoki fayl, 401 — avtorizatsiya, 403 — kompaniya egaligi,
404 — mijoz/shartnoma yo'q, 409 — parallel o'zgartirish to'qnashuvi,
413 — katta fayl, 502 — file-service bilan aloqa yoki javob xatosi.

`company-service`, `file-service`, `api-gateway` birga yangilanadi.
`V4__add_customers.sql` migratsiyasi qo'shilgan. Loyihadagi amaldagi Flyway o'chirilgan,
`ddl-auto: update` konfiguratsiyasida yangi entity jadvalni yaratadi.
Shartnoma download endpointi kompaniya egaligini tekshiradi. Amaldagi umumiy file-service
`/api/v1/attach/open/{id}` endpointi public, shu sabab xom attach ID'ni maxfiy token deb hisoblamaslik kerak.
Customer response saqlash xizmatining attach ID va public URL'ini chiqarmaydi.

Bu bosqichda to'lovlar, hisob-fakturalar va qarzdorlik hisoblari kiritilmagan.
Frontend integratsiyada localStorage o'rniga ushbu API'lar chaqiriladi.
