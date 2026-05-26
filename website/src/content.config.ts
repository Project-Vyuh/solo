import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const changelog = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/changelog' }),
  schema: z.object({
    version: z.string(),
    title: z.string(),
    date: z.string(),
    summary: z.string(),
    categories: z.array(z.enum(['Added', 'Changed', 'Deprecated', 'Removed', 'Fixed', 'Security'])),
  }),
});

export const collections = { changelog };
