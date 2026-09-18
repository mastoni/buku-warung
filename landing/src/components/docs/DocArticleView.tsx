import React from 'react';
import {
  Clock,
  Calendar,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  Info,
  AlertTriangle,
  Lightbulb,
  CheckCircle2,
  ArrowRight,
  Sparkles,
  Users,
  Layers,
  Image as ImageIcon,
} from 'lucide-react';
import {
  DocArticle,
  getAdjacentDocArticles,
  getDocArticleBySlug,
  DOC_VERSION,
} from '../../data/docsData';
import { useDocsRouter } from '../../hooks/useDocsRouter';
import { DocsBreadcrumb } from './DocsBreadcrumb';

interface DocArticleViewProps {
  article: DocArticle;
}

export const DocArticleView: React.FC<DocArticleViewProps> = ({ article }) => {
  const { navigateTo } = useDocsRouter();
  const { prev, next } = getAdjacentDocArticles(article.order);

  const relatedArticles = article.relatedSlugs
    .map((slug) => getDocArticleBySlug(slug))
    .filter((a): a is DocArticle => a !== undefined);

  return (
    <article className="max-w-4xl mx-auto py-6 sm:py-8">
      {/* Breadcrumb */}
      <DocsBreadcrumb
        categoryName={article.categoryName}
        articleTitle={article.title}
      />

      {/* Article Header */}
      <header className="pb-6 mb-8 border-b border-slate-200">
        <div className="flex flex-wrap items-center gap-2 mb-3">
          <span className="text-[11px] font-extrabold uppercase tracking-wider px-2.5 py-1 rounded-lg bg-emerald-100 text-emerald-800 border border-emerald-200">
            {article.categoryName}
          </span>
          {article.chapterLabel && (
            <span className="text-[11px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 border border-slate-200">
              {article.chapterLabel}
            </span>
          )}
          <div className="flex items-center gap-1 text-[11px] font-medium text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-lg border border-emerald-200/60">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
            <span>{DOC_VERSION}</span>
          </div>
          <div className="flex items-center gap-1 text-xs text-slate-400 ml-auto">
            <Clock className="w-3.5 h-3.5" />
            <span>{article.readTime}</span>
          </div>
        </div>

        <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold tracking-tight text-slate-900 leading-tight">
          {article.title}
        </h1>

        <p className="mt-3 text-base text-slate-600 font-normal leading-relaxed">
          {article.summary}
        </p>

        <div className="flex items-center gap-2 mt-4 text-xs text-slate-400">
          <Calendar className="w-3.5 h-3.5" />
          <span>Terakhir diperbarui: {article.updatedAt}</span>
        </div>
      </header>

      {/* Target Audience & Key Features Cards */}
      {(article.targetAudience || (article.features && article.features.length > 0)) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
          {article.targetAudience && (
            <div className="p-4 rounded-2xl bg-emerald-50/60 border border-emerald-100 space-y-1.5">
              <div className="flex items-center gap-2 text-xs font-bold text-emerald-800 uppercase tracking-wider">
                <Users className="w-4 h-4 text-emerald-600" />
                <span>Untuk Siapa Fitur Ini?</span>
              </div>
              <p className="text-xs sm:text-sm text-slate-700 leading-relaxed font-medium">
                {article.targetAudience}
              </p>
            </div>
          )}

          {article.features && article.features.length > 0 && (
            <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200/80 space-y-2">
              <div className="flex items-center gap-2 text-xs font-bold text-slate-800 uppercase tracking-wider">
                <Layers className="w-4 h-4 text-slate-600" />
                <span>Apa yang Bisa Dilakukan?</span>
              </div>
              <ul className="space-y-1 text-xs sm:text-sm text-slate-600">
                {article.features.map((f, fIdx) => (
                  <li key={fIdx} className="flex items-start gap-1.5">
                    <span className="text-emerald-600 font-bold">✓</span>
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Screenshot Showcase if available */}
      {article.screenshot && (
        <div className="my-8 rounded-2xl overflow-hidden border border-slate-200/80 shadow-md bg-white">
          <div className="p-3 bg-slate-100 border-b border-slate-200 flex items-center gap-2 text-xs font-semibold text-slate-700">
            <ImageIcon className="w-4 h-4 text-slate-500" />
            <span>{article.screenshot.caption}</span>
          </div>
          <div className="p-4 flex justify-center bg-slate-50">
            <img
              src={article.screenshot.src}
              alt={article.screenshot.caption}
              className="max-h-96 rounded-xl object-contain shadow-xs border border-slate-200"
            />
          </div>
        </div>
      )}

      {/* Article Body Content */}
      <div className="space-y-8 text-slate-700 text-sm sm:text-base leading-relaxed">
        {/* Sections */}
        {article.sections.map((sec, idx) => (
          <section key={idx} className="space-y-3">
            <h2 className="text-lg sm:text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
              <span>{sec.title}</span>
            </h2>
            {sec.paragraphs.map((p, pIdx) => (
              <p key={pIdx} className="text-slate-600 font-normal leading-relaxed">
                {p}
              </p>
            ))}
            {sec.listItems && (
              <ul className="space-y-2 mt-2 pl-4 border-l-2 border-emerald-200">
                {sec.listItems.map((item, iIdx) => (
                  <li key={iIdx} className="text-slate-700 text-sm font-medium flex items-start gap-2">
                    <span className="text-emerald-600 font-bold mt-0.5">•</span>
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        ))}

        {/* Step-by-Step Instructions if any */}
        {article.steps && article.steps.length > 0 && (
          <section className="mt-8 pt-6 border-t border-slate-200/80">
            <h2 className="text-lg sm:text-xl font-bold text-slate-900 tracking-tight mb-4 flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-emerald-600" />
              <span>Langkah-Langkah Penggunaan</span>
            </h2>
            <div className="space-y-3">
              {article.steps.map((step, sIdx) => (
                <div
                  key={sIdx}
                  className="flex items-start gap-3.5 p-4 rounded-xl bg-slate-50 border border-slate-200/80"
                >
                  <div className="w-6 h-6 rounded-full bg-emerald-600 text-white font-bold text-xs flex items-center justify-center shrink-0 mt-0.5 shadow-2xs">
                    {sIdx + 1}
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-slate-900">{step.title}</h3>
                    <p className="text-xs sm:text-sm text-slate-600 mt-1 leading-normal">{step.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Callout Boxes if any */}
        {article.callouts && article.callouts.length > 0 && (
          <div className="space-y-3 my-6">
            {article.callouts.map((c, cIdx) => {
              const isWarn = c.type === 'warning';
              const isTip = c.type === 'tip';
              const bg = isWarn
                ? 'bg-amber-50 border-amber-200 text-amber-900'
                : isTip
                ? 'bg-sky-50 border-sky-200 text-sky-900'
                : 'bg-emerald-50 border-emerald-200 text-emerald-900';
              const Icon = isWarn ? AlertTriangle : isTip ? Lightbulb : Info;
              const iconColor = isWarn ? 'text-amber-600' : isTip ? 'text-sky-600' : 'text-emerald-600';

              return (
                <div key={cIdx} className={`p-4 rounded-xl border flex items-start gap-3 ${bg}`}>
                  <Icon className={`w-5 h-5 ${iconColor} shrink-0 mt-0.5`} />
                  <div>
                    <h4 className="font-bold text-xs sm:text-sm">{c.title}</h4>
                    <p className="text-xs sm:text-sm mt-1 opacity-90 leading-relaxed">{c.text}</p>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Real-world Business Example Card if any */}
        {article.example && (
          <section className="my-6 p-5 rounded-2xl bg-slate-900 text-slate-100 shadow-md">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles className="w-4 h-4 text-emerald-400" />
              <h3 className="font-bold text-sm text-white tracking-wide uppercase">
                {article.example.title}
              </h3>
            </div>
            <p className="text-xs sm:text-sm text-slate-300 mb-3">{article.example.scenario}</p>
            <ul className="space-y-1.5 pl-4 border-l-2 border-emerald-500 text-xs sm:text-sm text-slate-200">
              {article.example.details.map((d, dIdx) => (
                <li key={dIdx} className="leading-snug">
                  {d}
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Scope Limitations Callout if any */}
        {article.limitations && article.limitations.length > 0 && (
          <div className="p-4 rounded-xl bg-slate-100 border border-slate-300/80 text-slate-700">
            <h4 className="font-bold text-xs text-slate-800 uppercase tracking-wider mb-1 flex items-center gap-1.5">
              <Info className="w-4 h-4 text-slate-500" />
              <span>Batasan Fitur Buku Warung v0.2.0</span>
            </h4>
            <ul className="list-disc list-inside text-xs text-slate-600 space-y-1 mt-2">
              {article.limitations.map((lim, lIdx) => (
                <li key={lIdx}>{lim}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Previous & Next Article Navigation */}
      <nav aria-label="Navigasi Artikel" className="mt-12 pt-6 border-t border-slate-200">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {prev ? (
            <button
              type="button"
              onClick={() => navigateTo(`/panduan/${prev.slug}`)}
              className="text-left p-4 rounded-xl border border-slate-200 hover:border-emerald-300 hover:bg-slate-50 transition-all group flex items-start gap-3 shadow-2xs"
            >
              <ChevronLeft className="w-5 h-5 text-slate-400 group-hover:text-emerald-600 group-hover:-translate-x-1 transition-transform shrink-0 mt-0.5" />
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Sebelumnya
                </span>
                <span className="text-xs sm:text-sm font-bold text-slate-900 group-hover:text-emerald-700 transition-colors">
                  {prev.title}
                </span>
              </div>
            </button>
          ) : (
            <div />
          )}

          {next ? (
            <button
              type="button"
              onClick={() => navigateTo(`/panduan/${next.slug}`)}
              className="text-right p-4 rounded-xl border border-slate-200 hover:border-emerald-300 hover:bg-slate-50 transition-all group flex items-start justify-end gap-3 shadow-2xs sm:ml-auto w-full"
            >
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Selanjutnya
                </span>
                <span className="text-xs sm:text-sm font-bold text-slate-900 group-hover:text-emerald-700 transition-colors">
                  {next.title}
                </span>
              </div>
              <ChevronRight className="w-5 h-5 text-slate-400 group-hover:text-emerald-600 group-hover:translate-x-1 transition-transform shrink-0 mt-0.5" />
            </button>
          ) : (
            <div />
          )}
        </div>
      </nav>

      {/* Related Articles Section */}
      {relatedArticles.length > 0 && (
        <section className="mt-12 pt-8 border-t border-slate-200">
          <h3 className="font-bold text-base text-slate-900 mb-4">Panduan Terkait</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {relatedArticles.map((rel) => (
              <button
                key={rel.slug}
                type="button"
                onClick={() => navigateTo(`/panduan/${rel.slug}`)}
                className="text-left p-3.5 rounded-xl border border-slate-200/80 hover:border-emerald-300 hover:bg-slate-50 transition-all group flex items-center justify-between gap-3 shadow-2xs"
              >
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-700">
                    {rel.categoryName}
                  </span>
                  <h4 className="text-xs font-bold text-slate-900 group-hover:text-emerald-700 transition-colors mt-0.5">
                    {rel.title}
                  </h4>
                </div>
                <ArrowRight className="w-4 h-4 text-slate-300 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-transform shrink-0" />
              </button>
            ))}
          </div>
        </section>
      )}
    </article>
  );
};
