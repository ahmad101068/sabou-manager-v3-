package ir.restaurant.management.ui

import ir.restaurant.management.core.SafeErrorLog
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.common.DomainFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Centralizes UI-safe error reporting without writing business data into Logcat. */
object UiErrorHandler {
    fun message(tag: String, error: Throwable): String {
        if (error is CancellationException) throw error
        SafeErrorLog.record(tag, "operation_failure", error)
        return when (error) {
            is BusinessRuleViolation -> error.error.toPersianMessage()
            is IllegalArgumentException, is IllegalStateException ->
                error.message?.takeIf { it.isNotBlank() } ?: "اطلاعات واردشده معتبر نیست."
            is IOException -> "دسترسی به فایل یا حافظه انجام نشد."
            else -> "خطای داخلی رخ داد. دوباره تلاش کنید."
        }
    }

    internal fun DomainFailure.toPersianMessage(): String = when (this) {
        is BusinessError.InsufficientStock ->
            "موجودی «$itemName» کافی نیست؛ موجودی را تأمین یا مقدار عملیات را کمتر کنید."
        is BusinessError.InsufficientInventoryValue ->
            "ارزش ثبت‌شده موجودی «$itemName» برای این خروج کافی نیست؛ ارزش‌گذاری کالا را بررسی کنید."
        is BusinessError.ClosedAccountingPeriod ->
            "دوره مالی این تاریخ بسته است؛ تاریخ مجاز دیگری انتخاب کنید یا از مسئول دوره درخواست بازگشایی کنید."
        is BusinessError.ClosedInventoryPeriod ->
            "دوره انبار این تاریخ بسته است؛ عملیات را در دوره باز ثبت کنید."
        is BusinessError.ClosedSalesDay ->
            "روز فروش بسته و امضاشده است؛ برای اصلاح از گردش بازگشایی مجاز استفاده کنید."
        is BusinessError.DuplicateDocument ->
            "سند $documentNumber قبلاً ثبت شده است؛ سند موجود را باز کنید."
        is BusinessError.InvalidRecipe ->
            "رسپی برای این عملیات معتبر نیست: $reason"
        is BusinessError.PermissionDenied ->
            "مجوز «${permission.title}» برای حساب کاربری شما فعال نیست."
        BusinessError.AuthenticationRequired ->
            "برای ادامه، دوباره وارد حساب کاربری شوید."
        is BusinessError.ApprovalRequired ->
            "این عملیات به تأیید سطح $requiredLevel نیاز دارد."
        is BusinessError.SeparationOfDutiesViolation ->
            "ثبت‌کننده و تأییدکننده این عملیات باید دو کاربر متفاوت باشند."
        is BusinessError.SupplierInactive ->
            "تأمین‌کننده غیرفعال است؛ تأمین‌کننده فعال دیگری انتخاب کنید."
        is BusinessError.InvalidLocation ->
            "محل انبار معتبر یا فعال نیست؛ محل مبدأ و مقصد را دوباره انتخاب کنید."
        is BusinessError.InvalidLot ->
            when (reason) {
                "LOT_REQUIRED" -> "برای این کالا شماره لات و، در صورت الزام، تاریخ انقضا را ثبت کنید."
                "LOT_IDENTITY_CONFLICT" -> "مشخصات این شماره لات با لات موجود یکسان نیست؛ شماره لات دیگری ثبت کنید."
                else -> "اطلاعات لات معتبر نیست؛ شماره لات، تاریخ‌ها و وضعیت آن را بررسی کنید."
            }
        is BusinessError.LotExpired ->
            "لات انتخاب‌شده منقضی است؛ آن را از مسیر ضایعات یا مرجوعی تعیین تکلیف کنید."
        is BusinessError.LotBlocked ->
            "لات در وضعیت $status قابل مصرف نیست؛ وضعیت لات را در مرکز انقضا بررسی کنید."
        is BusinessError.CountNotApproved ->
            "جلسه انبارگردانی هنوز تأیید نشده است؛ ابتدا گردش تأیید را کامل کنید."
        is BusinessError.CountAlreadyPosted ->
            "این جلسه انبارگردانی قبلاً ثبت نهایی شده است؛ برای اصلاح از سند جدید استفاده کنید."
        is BusinessError.CountUnitCostRequired ->
            "برای کالایی که موجودی سیستمی ندارد، بهای واحد شمارش را وارد کنید."
        is BusinessError.WasteNotApproved ->
            "سند ضایعات هنوز تأیید نشده است؛ آن را برای تأیید ارسال کنید."
        is BusinessError.WasteAlreadyPosted ->
            "این سند ضایعات قبلاً ثبت نهایی شده است؛ برای اصلاح از سند برگشت استفاده کنید."
        is BusinessError.TransferNotApproved ->
            "انتقال هنوز تأیید نشده است؛ ابتدا درخواست انتقال را تأیید کنید."
        is BusinessError.TransferAlreadyIssued ->
            "خروج این انتقال قبلاً ثبت شده است؛ وضعیت «در راه» را بررسی کنید."
        is BusinessError.TransferAlreadyReceived ->
            "دریافت این انتقال قبلاً تکمیل شده است؛ برای اصلاح سند جدید ثبت کنید."
        is BusinessError.TransferVarianceRequiresApproval ->
            "مقدار دریافت با مقدار ارسال‌شده متفاوت است؛ مغایرت باید جداگانه بررسی و تأیید شود."
        is BusinessError.EntityNotFound ->
            "رکورد موردنیاز پیدا نشد یا دیگر در دسترس نیست؛ صفحه را تازه‌سازی کنید."
        is BusinessError.IdempotencyConflict ->
            "این فرمان قبلاً با اطلاعات متفاوت ثبت شده است؛ وضعیت سند موجود را بررسی کنید."
        is BusinessError.ConcurrentModification ->
            "اطلاعات هم‌زمان تغییر کرده است؛ صفحه را تازه‌سازی و دوباره تلاش کنید."
        is BusinessError.InvalidBusinessState ->
            "وضعیت فعلی سند اجازه این عملیات را نمی‌دهد."
        is BusinessError.InvalidInput -> reason
        is BusinessError.DuplicatePosting ->
            "اثر مالی این سند قبلاً ثبت شده است؛ سند موجود را بررسی کنید."
        is BusinessError.InvalidJournal ->
            "سند حسابداری معتبر نیست؛ آرتیکل‌ها، حساب‌ها و توازن بدهکار/بستانکار را بررسی کنید."
        is BusinessError.InvalidQuantity ->
            "مقدار واردشده معتبر نیست؛ مقدار مثبت و در محدوده مجاز وارد کنید."
        is BusinessError.InvalidMoney ->
            "مبلغ واردشده معتبر نیست یا از محدوده امن خارج شده است."
        is BusinessError.InvalidStateTransition ->
            "تغییر وضعیت در مرحله فعلی مجاز نیست؛ وضعیت سند را تازه‌سازی کنید."
        is BusinessError.ConcurrencyConflict ->
            "رکورد هم‌زمان تغییر کرده است؛ اطلاعات را تازه‌سازی و دوباره تلاش کنید."
        is BusinessError.UnknownStoredValue ->
            "یک مقدار قدیمی یا ناشناخته در داده‌ها پیدا شد؛ قبل از ادامه، ارتقای داده را بررسی کنید."
        is BusinessError.UnsupportedDomainOperation ->
            "این عملیات هنوز در مرز دامنه فعال نشده است."
        else -> "عملیات به‌دلیل نقض یک قاعده کسب‌وکار انجام نشد."
    }
}
