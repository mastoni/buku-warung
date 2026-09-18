import React, { useEffect } from 'react';
import { DocArticle, DOC_VERSION } from '../../data/docsData';

interface DocsSeoProps {
  article?: DocArticle;
}

export const DocsSeo: React.FC<DocsSeoProps> = ({ article }) => {
  useEffect(() => {
    const defaultTitle =
      'Panduan Lengkap Buku Warung — Petunjuk Penggunaan Kasir & Pembukuan UMKM';
    const defaultDesc =
      'Panduan resmi penggunaan Buku Warung v0.2.0. Pelajari cara mengelola kasir POS, stok barang, hutang piutang, pembelian, Purchase Order, laporan, backup, dan printer thermal.';
    const baseUrl = 'https://bukuwarung.skmnetwork.com';

    const pageTitle = article
      ? `${article.title} — Panduan Buku Warung`
      : defaultTitle;
    const pageDesc = article ? article.description : defaultDesc;
    const canonicalUrl = article
      ? `${baseUrl}/panduan/${article.slug}`
      : `${baseUrl}/panduan`;

    // 1. Update Title
    document.title = pageTitle;

    // 2. Update Meta Description
    let metaDesc = document.querySelector('meta[name="description"]');
    if (!metaDesc) {
      metaDesc = document.createElement('meta');
      metaDesc.setAttribute('name', 'description');
      document.head.appendChild(metaDesc);
    }
    metaDesc.setAttribute('content', pageDesc);

    // 3. Update Canonical Tag
    let canonical = document.querySelector('link[rel="canonical"]');
    if (!canonical) {
      canonical = document.createElement('link');
      canonical.setAttribute('rel', 'canonical');
      document.head.appendChild(canonical);
    }
    canonical.setAttribute('href', canonicalUrl);

    // 4. Update Open Graph
    const setOg = (property: string, content: string) => {
      let el = document.querySelector(`meta[property="${property}"]`);
      if (!el) {
        el = document.createElement('meta');
        el.setAttribute('property', property);
        document.head.appendChild(el);
      }
      el.setAttribute('content', content);
    };

    setOg('og:title', pageTitle);
    setOg('og:description', pageDesc);
    setOg('og:url', canonicalUrl);
    setOg('og:type', article ? 'article' : 'website');

    // 5. Inject Structured Data JSON-LD
    let scriptTag = document.getElementById('docs-jsonld') as HTMLScriptElement | null;
    if (!scriptTag) {
      scriptTag = document.createElement('script');
      scriptTag.id = 'docs-jsonld';
      scriptTag.type = 'application/ld+json';
      document.head.appendChild(scriptTag);
    }

    const breadcrumbs = [
      {
        '@type': 'ListItem',
        position: 1,
        name: 'Beranda',
        item: baseUrl,
      },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Panduan',
        item: `${baseUrl}/panduan`,
      },
    ];

    if (article) {
      breadcrumbs.push({
        '@type': 'ListItem',
        position: 3,
        name: article.title,
        item: canonicalUrl,
      });
    }

    const jsonLdData: any = {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'BreadcrumbList',
          itemListElement: breadcrumbs,
        },
      ],
    };

    if (article) {
      jsonLdData['@graph'].push({
        '@type': 'TechArticle',
        headline: article.title,
        description: article.description,
        url: canonicalUrl,
        inLanguage: 'id',
        version: DOC_VERSION,
        dateModified: '2026-09-18',
        author: {
          '@type': 'Organization',
          name: 'SKM Network',
          url: 'https://skmnetwork.com',
        },
      });
    }

    scriptTag.textContent = JSON.stringify(jsonLdData);

    return () => {
      // Revert title on unmount
      document.title = 'Buku Warung — Aplikasi Kasir & Pembukuan UMKM Sekali Beli Rp 50.000';
    };
  }, [article]);

  return null;
};
