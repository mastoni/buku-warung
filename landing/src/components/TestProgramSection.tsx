import React, { useEffect, useState } from 'react';
import { Beaker, CheckCircle2, MessageCircle, ShieldCheck, ShoppingBag, Users } from 'lucide-react';
import { LANDING_CONFIG, TEST_CAMPAIGN_CONFIG } from '../data/landingData';
import { useTestCampaign } from '../hooks/useTestCampaign';
import { getOrderUrl, trackTestRegistrationStarted, trackTestRegistrationSubmitted, trackWhatsAppClick } from '../tracking';

interface FormState {
  name: string;
  whatsapp: string;
  googlePlayEmail: string;
  businessType: string;
  dailyTransactions: string;
  androidDevice: string;
  consent: boolean;
}

const EMPTY_FORM: FormState = {
  name: '',
  whatsapp: '',
  googlePlayEmail: '',
  businessType: '',
  dailyTransactions: '',
  androidDevice: '',
  consent: false
};

const fieldClass =
  'w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20';

const labelClass = 'block text-xs font-bold uppercase tracking-wide text-slate-600 mb-1.5';

export const TestProgramSection: React.FC = () => {
  const { state, loading, submitState, errorMessage, result, alreadyRegistered, isFull, register } =
    useTestCampaign();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  useEffect(() => {
    if (submitState === 'submitting') {
      trackTestRegistrationStarted();
    }
  }, [submitState]);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const isComplete =
    form.name.trim().length >= 2 &&
    form.whatsapp.trim().length >= 8 &&
    form.googlePlayEmail.includes('@') &&
    form.businessType.length > 0 &&
    form.dailyTransactions.length > 0 &&
    form.androidDevice.length > 0 &&
    form.consent;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!isComplete) return;
    trackTestRegistrationSubmitted();
    await register(form);
  };

  return (
    <section id={TEST_CAMPAIGN_CONFIG.anchorId} className="py-16 md:py-24 bg-white">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="max-w-3xl">
          <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight text-slate-900">
            {TEST_CAMPAIGN_CONFIG.sectionTitle}
          </h2>
          <p className="mt-3 text-base text-slate-600 leading-relaxed">{TEST_CAMPAIGN_CONFIG.sectionBody}</p>
          <p className="mt-3 inline-flex items-center gap-2 text-sm font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-full px-3.5 py-1.5">
            <Users className="w-4 h-4" />
            {TEST_CAMPAIGN_CONFIG.batchLabel}
          </p>
        </div>

        {/* Availability is rendered only from the server response, never from a local counter. */}
        <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-5">
          {loading ? (
            <p className="text-sm text-slate-600">Memeriksa ketersediaan slot…</p>
          ) : isFull ? (
            <div className="flex items-start gap-3">
              <Beaker className="w-5 h-5 text-slate-500 shrink-0 mt-0.5" />
              <p className="text-sm font-bold text-slate-900">
                {TEST_CAMPAIGN_CONFIG.fullTitle} — {TEST_CAMPAIGN_CONFIG.fullBody} ({state.registered} dari{' '}
                {state.capacity} slot)
              </p>
            </div>
          ) : (
            <p className="text-sm text-slate-700">
              <span className="font-extrabold text-slate-900">{state.registered}</span> dari{' '}
              <span className="font-extrabold text-slate-900">{state.capacity}</span> slot sudah terisi.
              Sisa <span className="font-extrabold text-emerald-700">{state.remaining}</span> slot.
            </p>
          )}
        </div>

        <div className="mt-8 grid gap-6 lg:grid-cols-2">
          {/* ---------------------------------------------------------------- TEST PATH */}
          <div className="rounded-3xl border-2 border-emerald-200 bg-emerald-50/40 p-6">
            {isFull ? (
              <div className="space-y-4">
                <h3 className="text-lg font-extrabold text-slate-900">{TEST_CAMPAIGN_CONFIG.fullTitle}</h3>
                <p className="text-sm text-slate-700">{TEST_CAMPAIGN_CONFIG.fullBody}</p>
                <p className="text-sm text-slate-600">{TEST_CAMPAIGN_CONFIG.waitingListBody}</p>
                <a
                  href={LANDING_CONFIG.whatsappConsultationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={() => trackWhatsAppClick('test_waiting_list')}
                  className="w-full inline-flex items-center justify-center gap-2 bg-white border border-emerald-600 text-emerald-700 font-extrabold text-sm px-5 py-3.5 rounded-2xl transition-colors hover:bg-emerald-50"
                >
                  <MessageCircle className="w-4 h-4" />
                  <span>{TEST_CAMPAIGN_CONFIG.waitingListCta}</span>
                </a>
              </div>
            ) : submitState === 'success' && result ? (
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-emerald-700">
                  <CheckCircle2 className="w-5 h-5" />
                  <h3 className="text-lg font-extrabold">Pendaftaran diterima</h3>
                </div>
                <p className="text-sm text-slate-700">
                  {result.alreadyRegistered
                    ? 'Anda sudah terdaftar pada batch ini sebelumnya. Slot Anda tetap aman.'
                    : `Nomor antrean Anda: ${result.slotNumber}.`}
                </p>
                <p className="text-sm text-slate-600">
                  Tim kami akan menghubungi Anda melalui WhatsApp untuk proses akses Google Play closed testing.
                </p>
                <p className="text-xs text-slate-500">
                  Sisa slot saat ini: {result.remaining} dari {result.capacity}.
                </p>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                <h3 className="text-lg font-extrabold text-slate-900">Daftar jadi penguji</h3>

                {alreadyRegistered && (
                  <p className="text-xs font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2">
                    Perangkat ini pernah terdaftar. Pendaftaran ulang tidak akan memakai slot tambahan.
                  </p>
                )}

                <div>
                  <label className={labelClass} htmlFor="tc-name">Nama usaha</label>
                  <input
                    id="tc-name"
                    className={fieldClass}
                    value={form.name}
                    onChange={(e) => update('name', e.target.value)}
                    maxLength={120}
                    required
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className={labelClass} htmlFor="tc-wa">Nomor WhatsApp</label>
                    <input
                      id="tc-wa"
                      className={fieldClass}
                      value={form.whatsapp}
                      onChange={(e) => update('whatsapp', e.target.value)}
                      placeholder="08xxxxxxxxxx"
                      maxLength={24}
                      required
                    />
                  </div>
                  <div>
                    <label className={labelClass} htmlFor="tc-email">Email Google Play</label>
                    <input
                      id="tc-email"
                      type="email"
                      className={fieldClass}
                      value={form.googlePlayEmail}
                      onChange={(e) => update('googlePlayEmail', e.target.value)}
                      placeholder="email@google.com"
                      maxLength={254}
                      required
                    />
                  </div>
                </div>

                <div>
                  <label className={labelClass} htmlFor="tc-business">Jenis usaha</label>
                  <select
                    id="tc-business"
                    className={fieldClass}
                    value={form.businessType}
                    onChange={(e) => update('businessType', e.target.value)}
                    required
                  >
                    <option value="">Pilih jenis usaha</option>
                    {TEST_CAMPAIGN_CONFIG.businessTypes.map((option) => (
                      <option key={option} value={option}>{option}</option>
                    ))}
                  </select>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className={labelClass} htmlFor="tc-daily">Perkiraan transaksi harian</label>
                    <select
                      id="tc-daily"
                      className={fieldClass}
                      value={form.dailyTransactions}
                      onChange={(e) => update('dailyTransactions', e.target.value)}
                      required
                    >
                      <option value="">Pilih</option>
                      {TEST_CAMPAIGN_CONFIG.dailyTransactionOptions.map((option) => (
                        <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className={labelClass} htmlFor="tc-device">Perangkat Android</label>
                    <select
                      id="tc-device"
                      className={fieldClass}
                      value={form.androidDevice}
                      onChange={(e) => update('androidDevice', e.target.value)}
                      required
                    >
                      <option value="">Pilih</option>
                      {TEST_CAMPAIGN_CONFIG.androidDeviceOptions.map((option) => (
                        <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <label className="flex items-start gap-2.5 text-xs text-slate-700">
                  <input
                    type="checkbox"
                    className="mt-0.5 w-4 h-4 rounded border-slate-300"
                    checked={form.consent}
                    onChange={(e) => update('consent', e.target.checked)}
                  />
                  <span>
                    Saya bersedia mengikuti program test ini dan memberikan masukan tentang penggunaan
                    Buku Warung.
                  </span>
                </label>

                {submitState === 'full' && (
                  <p className="text-xs font-bold text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                    Kuota batch ini baru saja penuh. Gunakan tombol BELI LANGSUNG atau hubungi kami untuk
                    batch berikutnya.
                  </p>
                )}
                {errorMessage && (
                  <p className="text-xs font-bold text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                    {errorMessage}
                  </p>
                )}

                <button
                  type="submit"
                  disabled={!isComplete || submitState === 'submitting'}
                  className="w-full inline-flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-extrabold text-sm px-5 py-3.5 rounded-2xl transition-colors"
                >
                  <Beaker className="w-4 h-4" />
                  <span>
                    {submitState === 'submitting' ? 'Mengirim…' : TEST_CAMPAIGN_CONFIG.primaryCta}
                  </span>
                </button>

                <p className="flex items-start gap-1.5 text-[11px] text-slate-500">
                  <ShieldCheck className="w-3.5 h-3.5 mt-px shrink-0" />
                  Data Anda hanya dipakai untuk proses batch testing dan disimpan terenkripsi.
                </p>
              </form>
            )}
          </div>

          {/* -------------------------------------------------------------- BUY PATH */}
          <div className="rounded-3xl border-2 border-slate-200 bg-white p-6 flex flex-col">
            <div className="flex items-center gap-2 text-slate-900">
              <ShoppingBag className="w-5 h-5" />
              <h3 className="text-lg font-extrabold">Beli Langsung</h3>
            </div>
            <p className="mt-2 text-sm text-slate-600">
              Ingin langsung memakai Buku Warung tanpa menunggu batch test? Pesan lewat pemesanan resmi
              dan license dikirim otomatis.
            </p>
            <div className="mt-4 flex-1" />
            <a
              href={getOrderUrl(LANDING_CONFIG.publicOrderUrl)}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full inline-flex items-center justify-center gap-2 bg-slate-900 hover:bg-slate-800 text-white font-extrabold text-sm px-5 py-3.5 rounded-2xl transition-colors"
            >
              <ShoppingBag className="w-4 h-4" />
              <span>{TEST_CAMPAIGN_CONFIG.secondaryCta}</span>
            </a>
            <a
              href={LANDING_CONFIG.whatsappConsultationUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackWhatsAppClick('test_section')}
              className="mt-2.5 w-full inline-flex items-center justify-center gap-2 bg-white border border-slate-300 text-slate-700 font-bold text-sm px-5 py-3 rounded-2xl transition-colors hover:bg-slate-50"
            >
              <MessageCircle className="w-4 h-4 text-emerald-600" />
              <span>Tanya via WhatsApp</span>
            </a>
          </div>
        </div>
      </div>
    </section>
  );
};
