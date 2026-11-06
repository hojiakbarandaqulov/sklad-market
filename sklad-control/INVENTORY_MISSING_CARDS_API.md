# Ombor kartasi mavjud bo‘lmaganda API javobi

`GET /api/v1/control/products` va `GET /api/v1/control/products/{id}`
product-service muvaffaqiyatli javobida karta topilmasa ham ombor yozuvini qaytaradi.
Inventory ID, productId, SKU, status va stocks saqlanadi; pagination o‘zgarmaydi.

Qo‘shilgan maydonlar:

```json
{
  "catalogCardAvailable": false,
  "catalogCardUnavailableReason": "Katalog kartasi mavjud emas yoki o‘chirilgan. Sotuvga chiqarib bo‘lmaydi."
}
```

Bu holatda `product` null yoki serialization sozlamasiga ko‘ra javobda bo‘lmasligi
mumkin. Frontend SKU orqali yozuvni ko‘rsatadi, ogohlantirishni chiqaradi va
sotuvga chiqarish tugmasini bloklaydi. Backend ham PATCH orqali chiqarishga
ruxsat bermaydi. Sog‘lom kartada `catalogCardAvailable=true` qaytadi.

Product-service ishlamasa yoki noto‘g‘ri kompaniya kartasini qaytarsa, bu holat
yo‘qolgan karta sifatida yashirilmaydi: avvalgi xato javobi saqlanadi.

`POST /api/v1/control/products/{id}/archive`: karta yo‘q bo‘lsa ham, qoldiq va
rezerv bo‘lmagandagina ombor yozuvini arxivlash mumkin. Restore, yangi bog‘lanish,
edit va sotuvga chiqarish amallari haqiqiy karta talab qiladi.

`DELETE /api/v1/products/{id}` endi Control orqali bog‘lanishni tekshiradi:

- faol yoki arxivlangan ombor bog‘lanishi mavjud: 409, o‘chirish bajarilmaydi;
- tekshiruv xizmati ishlamayapti yoki javob yo‘q: 503, o‘chirish bajarilmaydi;
- bog‘lanish mavjud emas: avvalgi soft-delete oqimi bajariladi.

Yangi ichki API: `GET /internal/control/product-links/{productId}?companyId=...`.
JWT talab qilinadi. SELLER uchun kompaniya egaligi tekshiriladi; ADMIN va
SUPER_ADMIN rollari ham foydalanishi mumkin. Bu API faqat boolean qaytaradi.

Deploy tartibi (serverdagi yangi kod bilan loyiha papkasida):

```bash
docker compose up -d --build --no-deps sklad-control
# Control ishga tushib, health UP bo‘lgach:
docker compose up -d --build --no-deps product-service
```

57 va 59 kabi oldin o‘chirilgan productlar tiklanmaydi; ularning ombor yozuvlari
yangi mavjud-emas belgisi bilan ko‘rinadi. DB migration yoki qoldiqni o‘zgartirish
talab qilinmaydi. Public katalog o‘chirilgan kartalarni yashirishda davom etadi.
