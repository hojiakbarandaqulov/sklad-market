# Jobs chat: tokensiz ichki integratsiya

2026-10-04: /internal/chats/jobs maxsus token talab qilmaydi.
Feign client X-Jobs-Chat-Token yubormaydi; dispatcher token sozlanishini kutmaydi.
JOBS_CHAT_INTERNAL_TOKEN Compose konfiguratsiyasidan olib tashlandi.

Public POST /api/v1/applications/{id}/chat uchun BUYER/SELLER JWT va ariza yoki
kompaniya egaligi tekshiruvlari saqlangan. Request body validatsiyasi, idempotent
chat yaratish va xatodan keyingi qayta urinishlar ham saqlangan.

Ichki endpoint tarmoq orqali kira oladigan mijozlardan token talab qilmaydi;
/internal/chats/jobs yo'lini tashqi Nginx/gateway orqali ochmang.

Serverda yangilangan koddan:
```bash
docker compose up -d --build --no-deps chat-service sklad-jobs
```

READY va chatThreadId chat muvaffaqiyatli ochilganda qaytadi. Chat-service yoki
baza xatosida PENDING qolishi mumkin; ikki servis loglarini tekshiring.