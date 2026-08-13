# بازطراحی UI خانه و انبار — سبوی عشق

## دامنه تغییر

- بازطراحی کامل صفحه خانه با RTL و Material 3
- هدر برند «سبوی عشق» با لوگوی هندسی
- کارت Hero فروش/شاخص اصلی با گرادیان Teal و نمودار روند
- بخش «نیازمند توجه» با وضعیت‌های رنگی و مسیر عملیاتی
- شبکه ۶تایی عملیات سریع با دسترسی نقش‌محور
- بخش «نمای کلی امروز»
- نوار پایین ۵ مقصد: خانه، انبار، فروش، مالی، بیشتر
- بازطراحی داشبورد انبار با همان Design System
- Hero ارزش موجودی، وضعیت موجودی، عملیات سریع، گردش اخیر و مراکز کنترل
- استفاده از داده‌های واقعی `DashboardSnapshot` و `InventoryDashboardSnapshot`
- استفاده از ledger واقعی برای گردش اخیر
- نمایش قرنطینه بر اساس lotهای واقعی
- تغییر نام قابل‌نمایش برنامه به «سبوی عشق» در `strings.xml` و About

## فایل‌های اصلی تغییرکرده

- `app/src/main/java/ir/sabou/inventory/ui/DashboardScreen.kt`
- `app/src/main/java/ir/sabou/inventory/ui/InventoryWorkspaceScreen.kt`
- `app/src/main/java/ir/sabou/inventory/ui/ErpDashboardComponents.kt` (جدید)
- `app/src/main/java/ir/sabou/inventory/ui/DashboardUxModels.kt`
- `app/src/main/java/ir/sabou/inventory/ui/InventoryRoutes.kt`
- `app/src/main/java/ir/sabou/inventory/ui/OperationsRoutes.kt`
- `app/src/main/java/ir/sabou/inventory/ui/NavigationSettingsScreens.kt`
- `app/src/main/res/values/strings.xml`

## اعتبارسنجی

- `verify-alpha162-code-quality.py` → PASS
- `verify-dashboard-ux2.py` → PASS
- `verify-alpha162-enterprise-core.py` → PASS
- اجرای Gradle کامل در محیط فعلی به دلیل عدم دسترسی شبکه برای دانلود Gradle 8.13 متوقف شد.

## نکته معماری

UI جدید کنترل دسترسی role-aware موجود را دور نمی‌زند. Home همچنان فقط KPI و Actionهایی را می‌گیرد که `DashboardUxComposer` برای نقش جاری مجاز کرده است.
