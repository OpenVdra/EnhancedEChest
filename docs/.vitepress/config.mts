import { defineConfig } from 'vitepress'

// Inline Lucide SVG icons for sidebar section headers. VitePress renders a
// sidebar item's `text` with `v-html`, so the <LucideIcon> Vue component can't
// be used here (v-html does not compile components) — the markup must be static
// SVG. `currentColor` + the rules in theme/style.css handle theming and sizing.
const lucide = (paths: string) =>
  `<svg class="sidebar-icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`

const ICON = {
  general: lucide('<rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/>'),
  gettingStarted: lucide('<path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/><path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09"/><path d="M9 12a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.4 22.4 0 0 1-4 2z"/><path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 .05 5 .05"/>'),
  documentation: lucide('<path d="M12 7v14"/><path d="M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z"/>'),
}

// Prefix a sidebar section title with its icon. The icon sits in a soft rounded
// badge; the label is wrapped separately so the two align as a flex row and the
// badge stays put when a long title wraps to two lines (see theme/style.css).
const withIcon = (icon: string, text: string) =>
  `<span class="sidebar-icon-badge">${icon}</span><span class="sidebar-label">${text}</span>`

export default defineConfig({
  title: "EnhancedEchest",
  description: "Bigger ender chests for your players, several per person, each with its own name and icon.",
  // GitHub project page is served from /EnhancedEchest/. If you later point a custom
  // domain at the site (add public/CNAME), change this back to '/'.
  base: '/EnhancedEchest/',
  cleanUrls: true,
  head: [
    // Entries in `head` are emitted verbatim, so the `base` is NOT prepended
    // automatically the way it is for themeConfig.logo / markdown links. The
    // href must therefore include the base path, otherwise on the GitHub Pages
    // project site the favicon resolves to the domain root and 404s.
    ['link', { rel: 'icon', type: 'image/png', href: '/EnhancedEchest/logo.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/EnhancedEchest/logo.png' }],
  ],
  themeConfig: {
    // Shared across every locale; per-locale nav / sidebar / editLink live under
    // each entry in `locales` below and are deep-merged over these.
    logo: '/logo.png',

    socialLinks: [
      { icon: 'github', link: 'https://github.com/OpenVdra/EnhancedEchest' }
    ],

    search: {
      provider: 'local'
    }
  },

  // i18n. The English site is served from the root; the Vietnamese site mirrors
  // it under /vi/. Each locale carries its own nav, sidebar and UI labels. The
  // content lives in `vi/` mirroring the root structure; the Vue components are
  // registered globally so they are reused as-is in both languages.
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Download', link: '/docs/download' },
          { text: 'Docs', link: '/docs/' }
        ],

        sidebar: [
          {
            text: withIcon(ICON.general, 'General'),
            items: [
              { text: 'Welcome', link: '/docs/' },
              { text: 'Features', link: '/docs/features' },
              { text: 'Paper vs Folia', link: '/docs/folia-paper' },
            ]
          },
          {
            text: withIcon(ICON.gettingStarted, 'Getting Started'),
            items: [
              { text: 'Download', link: '/docs/download' },
              { text: 'Installation', link: '/docs/installation' }
            ]
          },
          {
            text: withIcon(ICON.documentation, 'Documentation'),
            items: [
              {
                text: 'Access',
                collapsed: false,
                items: [
                  { text: 'Commands', link: '/docs/commands' },
                  { text: 'Permissions', link: '/docs/permissions' }
                ]
              },
              {
                text: 'Configuration',
                collapsed: false,
                items: [
                  { text: 'Main Config', link: '/docs/configuration' },
                  { text: 'Database', link: '/docs/database' },
                  { text: 'Migration', link: '/docs/migration' },
                  { text: 'Language', link: '/docs/language' }
                ]
              }
            ]
          }
        ],

        editLink: {
          pattern: 'https://github.com/OpenVdra/EnhancedEchest/edit/main/docs/:path',
          text: 'Edit this page on GitHub'
        }
      }
    },

    vi: {
      label: 'Tiếng Việt',
      lang: 'vi',
      description: 'Rương Ender lớn hơn cho người chơi, nhiều rương mỗi người, mỗi rương có tên và biểu tượng riêng.',
      themeConfig: {
        nav: [
          { text: 'Trang chủ', link: '/vi/' },
          { text: 'Tải về', link: '/vi/docs/download' },
          { text: 'Tài liệu', link: '/vi/docs/' }
        ],

        sidebar: [
          {
            text: withIcon(ICON.general, 'Tổng quan'),
            items: [
              { text: 'Giới thiệu', link: '/vi/docs/' },
              { text: 'Tính năng', link: '/vi/docs/features' },
              { text: 'Paper và Folia', link: '/vi/docs/folia-paper' },
            ]
          },
          {
            text: withIcon(ICON.gettingStarted, 'Bắt đầu'),
            items: [
              { text: 'Tải về', link: '/vi/docs/download' },
              { text: 'Cài đặt', link: '/vi/docs/installation' }
            ]
          },
          {
            text: withIcon(ICON.documentation, 'Tài liệu'),
            items: [
              {
                text: 'Truy cập',
                collapsed: false,
                items: [
                  { text: 'Lệnh', link: '/vi/docs/commands' },
                  { text: 'Quyền', link: '/vi/docs/permissions' }
                ]
              },
              {
                text: 'Cấu hình',
                collapsed: false,
                items: [
                  { text: 'Cấu hình chính', link: '/vi/docs/configuration' },
                  { text: 'Cơ sở dữ liệu', link: '/vi/docs/database' },
                  { text: 'Chuyển dữ liệu', link: '/vi/docs/migration' },
                  { text: 'Ngôn ngữ', link: '/vi/docs/language' }
                ]
              }
            ]
          }
        ],

        editLink: {
          pattern: 'https://github.com/OpenVdra/EnhancedEchest/edit/main/docs/:path',
          text: 'Chỉnh sửa trang này trên GitHub'
        },

        // VitePress UI strings (it does not translate these from `lang` alone).
        outlineTitle: 'Trên trang này',
        docFooter: {
          prev: 'Trang trước',
          next: 'Trang sau'
        },
        lastUpdatedText: 'Cập nhật lần cuối',
        returnToTopLabel: 'Về đầu trang',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Giao diện',
        lightModeSwitchTitle: 'Chuyển sang giao diện sáng',
        darkModeSwitchTitle: 'Chuyển sang giao diện tối'
      }
    }
  }
})
