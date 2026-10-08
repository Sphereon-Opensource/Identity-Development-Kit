# Module lib-conf-theme-web

Web-side binding layer for IDK theming. It projects the tenant-scoped branding data from `lib-conf-theme-core-public` into CSS custom properties, handles legacy token aliases, and ships the small bootstrap script used to avoid a flash of unstyled content while the theme is being applied. Reach for this in a browser-rendered UI when you want IDK-managed branding to drive the stylesheet without writing bespoke CSS-token glue.
