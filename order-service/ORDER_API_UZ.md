# Order — buyurtmalar API hujjati

Bu hujjat Order-service va Sklad Control integratsiyasini tushuntiradi. Gateway orqali tashqi prefiks: `/api/v1/orders`. Order-service porti: `8095`.

## 1. Tuzilma

```text
org.example
├── controller/OrderController.java
├── dto/ApiResponse.java
├── dto/order/
├── entity/Order.java
├── entity/OrderItem.java
├── enums/OrderStatus.java
├── repository/OrderRepository.java
├── mapper/OrderMapper.java
├── service/OrderService.java
├── service/ServiceTokenService.java
├── service/impl/OrderServiceImpl.java
├── service/impl/ServiceTokenServiceImpl.java
├── service/job/OrderRecoveryJob.java
├── client/
├── config/
├── exp/handle/
└── utils/SpringSecurityUtil.java
```

## 2. Umumiy qoidalar

- Har bir tashqi so‘rovda `Authorization: Bearer <JWT>` majburiy. Token `realm_access.roles` va musbat `profileId`ga ega bo‘lsin.
- Yaratish, o‘z xaridlarini ko‘rish, tafsilot va bekor qilish: `BUYER` yoki `SELLER`. Sotuvchi ham xaridor sifatida buyurtma bera oladi.
- Sotuvchi ro‘yxati va jo‘natish: `SELLER`; kompaniyaning faol egasi ekanligi company-service orqali tekshiriladi.
- Bitta buyurtmada bitta kompaniya mahsulotlari va bitta valyuta bo‘ladi. Bir nechta kompaniya uchun alohida buyurtmalar yarating.
- Mahsulot ID’si — **Market product ID**. Frontend `inventoryProductId` yoki ombor ID’sini yubormaydi.
- Miqdor `> 0`, ko‘pi bilan 16 butun va 3 kasr xonasi. Bitta mahsulotni takroriy pozitsiyalarda yubormang. Bir buyurtmada 1–100 ta pozitsiya.
- Mahsulot faol, moderatsiyadan o‘tgan (`APPROVED`), musbat `FIXED` narxga ega bo‘lishi kerak. Kelishiladigan va boshlang‘ich narxli mahsulotlar uchun mavjud RFQ/lead jarayonidan foydalaniladi.
- Narx, nom, valyuta va minimal buyurtma miqdori product-service’dan olinadi. Frontend yuborgan narx hisoblashga ta’sir qilmaydi. Buyurtmada shu paytdagi nom va narx nusxasi saqlanadi.
- Control faqat shu kompaniyaning faol omborlari va faol mahsulotlaridan mavjud miqdorni ajratadi. Mahsulot bir nechta omborda bo‘lsa, ombor qoldiqlari ID tartibida birlashtiriladi. Bitta pozitsiya yetishmasa, butun rezerv bekor qilinadi.
- Qoldiq: `available = onHand - reserved`. Rezervlash `onHand`ni kamaytirmaydi; jo‘natish kamaytiradi; bekor qilish rezervni bo‘shatadi.
- Rezerv muddati standart `PT24H` — 24 soat. `ORDER_RESERVATION_TTL` bilan o‘zgartiriladi; 0 dan katta va 30 kundan oshmasin. Frontend muddat yubormaydi.
- Vaqt maydonlari UTC `Instant`, masalan `2026-11-02T10:00:00Z`. Sana filtrlari Toshkent vaqti bo‘yicha hisoblanadi.

## 3. Endpointlar

| № | Usul | URL | Vazifasi |
|---|---|---|---|
| 1 | POST | `/api/v1/orders` | Buyurtma yaratish va rezervlash. |
| 2 | GET | `/api/v1/orders` | Token egasining xarid buyurtmalari. |
| 3 | GET | `/api/v1/orders/seller` | Sotuvchi kompaniyasiga kelgan buyurtmalar. |
| 4 | GET | `/api/v1/orders/{id}` | Xaridor yoki kompaniya egasi uchun tafsilot. |
| 5 | POST | `/api/v1/orders/{id}/ship` | Rezervni chiqim qilish va jo‘natilgan deb belgilash. |
| 6 | POST | `/api/v1/orders/{id}/cancel` | Buyurtmani bekor qilish va rezervni bo‘shatish. |

### 3.1. Buyurtma yaratish

```http
POST /api/v1/orders
Authorization: Bearer <BUYER yoki SELLER tokeni>
Content-Type: application/json
```

```json
{
  "companyId": 1,
  "idempotencyKey": "checkout-20261102-001",
  "deliveryAddress": "Toshkent, Yunusobod tumani",
  "note": "Yetkazishdan oldin qo‘ng‘iroq qiling",
  "items": [
    {"productId": 100, "quantity": 2.125}
  ]
}
```

`companyId`, `idempotencyKey`, `items` majburiy. Kalit 1–128 belgi. Manzil va izoh ixtiyoriy, har biri ko‘pi bilan 2000 belgi. Mahsulotdagi minimal buyurtma miqdoriga rioya qiling.

Muvaffaqiyatli javob — `200`:

```json
{
  "success": true,
  "data": {
    "id": 10,
    "buyerId": 7,
    "companyId": 1,
    "status": "RESERVED",
    "currency": "UZS",
    "totalAmount": 26562.765625,
    "deliveryAddress": "Toshkent, Yunusobod tumani",
    "note": "Yetkazishdan oldin qo‘ng‘iroq qiling",
    "reservationId": 25,
    "expiresAt": "2026-11-03T10:00:00Z",
    "createdAt": "2026-11-02T10:00:00Z",
    "updatedAt": "2026-11-02T10:00:00Z",
    "shippedAt": null,
    "cancelledAt": null,
    "failureReason": null,
    "items": [
      {"productId": 100, "productName": "Po‘lat", "quantity": 2.125, "unitPrice": 12500.125, "lineAmount": 26562.765625}
    ]
  }
}
```

Bir xil xaridor + kalit + so‘rov ikkinchi buyurtma yoki rezerv yaratmaydi. Pozitsiyalar tartibi va `2` / `2.000` farqi takroriy so‘rov deb qaraladi. Kalit bilan boshqa miqdor, kompaniya, manzil yoki izoh yuborilsa `409`. Dastlabki buyurtma narxi keyingi retry’da qayta hisoblanmaydi.

`503` yoki javob yo‘qolsa aynan o‘sha `idempotencyKey` bilan takrorlang. Yangi kalit yangi buyurtma yaratadi. Avvalgi buyurtma yakunlangan bo‘lsa, retry uning joriy yakuniy holatini qaytaradi.

Qoldiq yetishmasa `409`; bazada `REJECTED` buyurtma saqlanadi va xaridor ro‘yxatida ko‘rinadi. O‘sha kalit bilan qayta rezervlanmaydi. Keyingi yangi urinish uchun yangi kalit ishlatiladi.

### 3.2. Xaridor ro‘yxati

```http
GET /api/v1/orders?status=RESERVED&companyId=1&dateFrom=2026-11-01&dateTo=2026-11-02&page=1&perPage=20
```

Xaridor ID’si query’dan olinmaydi, token ichidagi `profileId`dan olinadi.

| Parametr | Majburiy | Izoh |
|---|---|---|
| companyId | Yo‘q | Muayyan sotuvchi kompaniyasi. Musbat ID. |
| status | Yo‘q | Quyidagi holatlardan bittasi. |
| dateFrom | Yo‘q | `YYYY-MM-DD`, kun boshidan boshlab. |
| dateTo | Yo‘q | `YYYY-MM-DD`, ko‘rsatilgan kun to‘liq kiradi. |
| page | Yo‘q | 1 dan boshlanadi, standart 1. |
| perPage | Yo‘q | 1–100, standart 20. |

`dateFrom <= dateTo`. Saralash: `createdAt DESC, id DESC`. Javob `success=true`, `data.content` ichida buyurtmalar, `data.totalElements`, `data.totalPages`, `data.number` va `data.size` bor. Spring sahifa javobidagi `number` 0 dan boshlanadi, query’dagi `page` esa 1 dan.

### 3.3. Sotuvchi ro‘yxati

```http
GET /api/v1/orders/seller?companyId=1&status=RESERVED&page=1&perPage=20
```

Bu endpointda `companyId` majburiy. Qolgan filtrlar va sahifalash xaridor ro‘yxati bilan bir xil. Boshqa sotuvchining kompaniyasi uchun `403` qaytadi.

### 3.4. Tafsilot

```http
GET /api/v1/orders/10
```

Buyurtmani yaratgan xaridor yoki shu kompaniyaning `SELLER` egasi ko‘radi. Begona foydalanuvchi `403`, mavjud bo‘lmagan buyurtma `404` oladi. Javob tuzilmasi yaratishdagi `data` bilan bir xil.

Yakunlanmagan buyurtma Control bilan sinxronlanadi. Shu sabab rezerv muddati tugagan bo‘lsa, `EXPIRED` holati qaytishi mumkin. Control ishlamasa `503`; xayoliy muvaffaqiyat holati qaytarilmaydi.

### 3.5. Jo‘natish

```http
POST /api/v1/orders/10/ship
Authorization: Bearer <kompaniya egasining SELLER tokeni>
```

Body talab qilinmaydi. `RESERVED` buyurtma uchun Control rezervi `COMMITTED` qilinadi: `reserved` ham, `onHand` ham ajratilgan miqdorga kamayadi. Har bir ombor pozitsiyasi uchun `SALE` jurnal yozuvi yaratiladi, `orderId` va `reservationId` bilan bog‘lanadi.

Takroriy jo‘natish qoldiqni ikkinchi marta kamaytirmaydi. Bekor qilingan, rad etilgan yoki muddati tugagan buyurtmani jo‘natish `409`. Xaridor jo‘nata olmaydi.

### 3.6. Bekor qilish

```http
POST /api/v1/orders/10/cancel
```

Body talab qilinmaydi. Xaridor yoki kompaniya egasi bekor qiladi. Control rezervi `RELEASED` bo‘ladi; jismoniy qoldiq saqlanadi. Takroriy bekor qilish xavfsiz. `EXPIRED` yoki `REJECTED` holatni bekor qilish joriy holatni qaytaradi.

Jo‘natilgan yoki jo‘natish amali boshlangan buyurtmani bekor qilish `409`. Bir vaqtda jo‘natish va bekor qilish kelganda faqat bitta yakuniy yo‘nalish bajariladi.

## 4. Holatlar va tiklanish

| Holat | Ma’nosi |
|---|---|
| RESERVING | Buyurtma saqlandi, Control rezervi hali tasdiqlanmadi. |
| RESERVED | Mahsulot ajratildi, jo‘natish mumkin. |
| SHIPPING | Jo‘natish niyati saqlandi, Control natijasi sinxronlanmoqda. |
| SHIPPED | Control chiqimni tasdiqladi. |
| CANCELLING | Bekor qilish niyati saqlandi, rezerv bo‘shatilmoqda. |
| CANCELLED | Rezerv bo‘shatilgan. |
| EXPIRED | Rezerv muddati tugagan. |
| REJECTED | Control rezerv yaratishni rad etgan. |

Ikki servis uchun umumiy DB tranzaksiyasi yo‘q. Order oldin o‘z niyatini bazaga yozadi, Control esa `companyId + orderId` orqali amallarni takrorlanmaydigan qiladi. Aloqa uzilganda Order `503` qaytaradi va tugamagan holatni saqlab qoladi. Standart har 60 soniyada fon vazifasi 100 tagacha buyurtmani sinxronlaydi. Jarayon qayta ishga tushganda ham shu yozuvlar davom ettiriladi.

Ro‘yxatlar bazadagi holatni ko‘rsatadi. Fon vazifasi ishlaguncha qisqa kechikish bo‘lishi mumkin; aniq buyurtma uchun tafsilotni yangilang. `SHIPPED` vaqti — Order Control natijasini qayd etgan vaqt.

## 5. Ichki integratsiya

Frontend bu yo‘llarni chaqirmaydi; ular Gateway’ga qo‘shilmagan. Faqat `CONTROL_ORDER_WRITE` realm roliga ega service-account tokeni qabul qilinadi.

| Xizmat | Usul | Yo‘l |
|---|---|---|
| Product | GET | `/internal/products/{productId}/order-summary` |
| Control | POST | `/internal/control/orders/{orderId}/reservation` |
| Control | GET | `/internal/control/orders/{orderId}/reservation?companyId=1` |
| Control | POST | `/internal/control/orders/{orderId}/reservation/commit?companyId=1` |
| Control | POST | `/internal/control/orders/{orderId}/reservation/release?companyId=1` |

Ichki rezerv yaratish body’si:

```json
{
  "companyId": 1,
  "buyerId": 7,
  "expiresAt": "2026-11-03T10:00:00Z",
  "items": [{"productId": 100, "quantity": 2.125}]
}
```

Control javobi mavjud `ApiResponse<ReservationDTO>` shaklida. `id`, `companyId`, `orderId`, `status` Order tomonidan tekshiriladi. Commit/release bir-biriga zid yakunlangan rezervni o‘zgartirmaydi; haqiqiy joriy holatini qaytaradi. Ichki amallarda haqiqiy sotuvchi JWT’si yo‘qligi sabab SALE jurnalidagi `createdBy` bo‘sh bo‘lishi mumkin; `orderId` va `reservationId` saqlanadi.

Oddiy `BUYER`, `SELLER` va `CONTROL_AVAILABILITY_READ` roli ichki buyurtma rezervi uchun yetarli emas.

## 6. Ishga tushirish

1. Alohida PostgreSQL bazasi yarating: `skalad_market_order`. Order uchun Liquibase `market_orders`, `market_order_items`, FK va indekslarni yaratadi; Hibernate `validate` bilan tekshiradi.
2. Keycloak’da confidential `order-service` client yarating, service accounts va client credentials imkoniyatini yoqing. Uning service-account’iga realm roli `CONTROL_ORDER_WRITE` bering.
3. `ORDER_CLIENT_ID`, `ORDER_CLIENT_SECRET`, `ORDER_TOKEN_URL` va `KEYCLOAK_ISSUER_URI`ni bir xil realm uchun sozlang. Sir kodga yozilmagan. Sir yo‘q bo‘lsa, ichki chaqiruvlar `503` bilan to‘xtaydi.
4. `SPRING_DATASOURCE_URL` yoki `ORDER_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`ni kiriting. Standart URL: `jdbc:postgresql://127.0.0.1:5432/skalad_market_order`.
5. Product-service, company-service, sklad-control va discovery-service ishlayotgan bo‘lsin. Ixtiyoriy to‘g‘ridan-to‘g‘ri manzillar: `PRODUCT_SERVICE_URL`, `COMPANY_SERVICE_URL`, `CONTROL_SERVICE_URL`. Berilmasa Eureka orqali topiladi.
6. O‘zgargan Product va Control servislarini ham qayta yig‘ing. `docker-compose.yml`ga Order xizmati va Gateway’ga `/api/v1/orders/**` yo‘li qo‘shilgan. Deploy avtomatik bajarilmagan.

Control’dagi `Warehouse`, `WarehouseStock`, `StockReservation` entity’larining jadval nomlari mavjud Liquibase jadvallari bilan aniq moslashtirilgan: `warehouses`, `warehouse_stocks`, `stock_reservations`. Oldingi `ddl-auto=update` singularda alohida jadvallar yaratgan muhit bo‘lsa, ishga tushirishdan oldin ularning ma’lumotlari alohida tekshirilsin; ushbu o‘zgarish eski jadvallarni o‘chirmaydi yoki ularning qoldiqlarini ko‘chirmaydi.

## 7. Xatoliklar

| Status | Sabab |
|---|---|
| 400 | Noto‘g‘ri maydon/filtr, takroriy pozitsiya, aralash kompaniya/valyuta yoki minimal buyurtma miqdori buzilgan. |
| 401 | JWT yo‘q yoki yaroqsiz. |
| 403 | Rol/profileId yetishmaydi, buyurtma begona yoki faol kompaniya egaligi tasdiqlanmagan. |
| 404 | Buyurtma, mahsulot yoki kompaniya topilmadi. |
| 409 | Qoldiq yetishmaydi, mahsulot sotuvga tayyor emas, kalit boshqa so‘rovga tegishli yoki holat o‘tishi mumkin emas. |
| 503 | Control/Product/Company/Keycloak ishlamayapti, service-account sozlanmagan yoki ichki javob mos emas. |

Xatolik javobida `success=false`, `message`, `errors`, `trace_id` bo‘ladi. Oddiy validatsiya xabarlari frameworkdan kelishi mumkin; biznes xabarlari o‘zbekcha.

Ushbu bosqichga to‘lov, tashuvchi integratsiyasi, qisman jo‘natish, qaytarish va RFQ’dan avtomatik buyurtmaga aylantirish kiritilmagan. Mavjud `/api/v1/cart/checkout-rfq` lead yaratishda davom etadi; tasdiqlangan xarid uchun alohida `POST /api/v1/orders` ishlatiladi.
