# Kompaniya mahsulotlari soni

Kompaniya DTO javoblariga `productCount` qo'shildi:
- GET /api/v1/companies/public va /search: data.content[].productCount
- GET /api/v1/companies/map: data.content[].productCount
- GET /api/v1/companies/{slug}: data.productCount
- GET /api/v1/companies (seller): data.productCount
- Kompaniya yaratish/tahrirlash, sevimlilar, admin ro'yxati va moderatsiya navbati javoblari.
- Internal kompaniya map-summary javoblari.

Barcha joyda bir xil hisob: APPROVED, isActive=true, deletedAt=null.
Kategoriya bo'yicha cheklanmaydi. Mahsulot yo'q bo'lsa 0.
Product-service javob bera olmasa yoki kompaniya uchun qiymat kelmasa null;
frontend null uchun '—' ko'rsatishi kerak, 0 deb talqin qilmasin.

Yangi internal API: POST /internal/products/counts/by-company
Body: [10,20]
Response: {"10":8,"20":0}
500 tagacha musbat ID; bo'sh ro'yxat uchun {}. Noto'g'ri body 400.
Company-service ro'yxatdagi IDlarni bitta so'rovga yig'adi, 500 dan katta
ro'yxatlarni bo'laklaydi. Mahsulotlar DBda GROUP BY bilan sanaladi.

DB ustuni yoki Flyway migratsiyasi kerak emas: son hisoblab qaytariladi.
Avval product-service, keyin company-service yangi versiyasini ishga tushirish kerak.
Frontend mavjud company API response'dagi productCount maydonini ishlatadi;
alohida har bir kartochka uchun so'rov kerak emas.