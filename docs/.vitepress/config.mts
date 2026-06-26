import { defineConfig } from 'vitepress'

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
            text: 'General',
            items: [
              { text: 'Welcome', link: '/docs/' },
              { text: 'Features', link: '/docs/features' },
            ]
          },
          {
            text: 'Getting Started',
            items: [
              { text: 'Download', link: '/docs/download' },
              { text: 'Installation', link: '/docs/installation' }
            ]
          },
          {
            text: 'Documentation',
            items: [
              {
                text: 'Commands & Permissions',
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
            text: 'Tổng quan',
            items: [
              { text: 'Giới thiệu', link: '/vi/docs/' },
              { text: 'Tính năng', link: '/vi/docs/features' },
            ]
          },
          {
            text: 'Bắt đầu',
            items: [
              { text: 'Tải về', link: '/vi/docs/download' },
              { text: 'Cài đặt', link: '/vi/docs/installation' }
            ]
          },
          {
            text: 'Tài liệu',
            items: [
              {
                text: 'Lệnh & Quyền',
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
