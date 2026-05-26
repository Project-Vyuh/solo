import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';
import icon from 'astro-icon';

export default defineConfig({
  site: 'https://project-vyuh.github.io',
  base: '/solo',
  integrations: [tailwind(), icon()],
  output: 'static',
});
