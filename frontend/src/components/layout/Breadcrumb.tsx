import { Breadcrumbs, Link, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import type { BreadcrumbItemDto } from '../../types/api';
import { type ElementType } from 'react';
import { useIsMobile } from '../../hooks/useIsMobile';

export default function Breadcrumb({ crumbs }: Readonly<{ crumbs: BreadcrumbItemDto[] }>) {
  const isMobile = useIsMobile();
  if (crumbs.length === 0) return null;
  const ancestors = crumbs.slice(0, -1);
  const current = crumbs[crumbs.length - 1];
  return (
    <Breadcrumbs aria-label="breadcrumb" sx={{ mb: 2 }} maxItems={isMobile ? 2 : 8}>
      <Link component={RouterLink as ElementType} to="/" underline="hover" color="inherit">Home</Link>
      {ancestors.map((c) => (
        <Link key={c.id} component={RouterLink as ElementType} to={`/folders/${c.id}`} underline="hover" color="inherit">{c.name}</Link>
      ))}
      <Typography color="text.primary" noWrap sx={isMobile ? { maxWidth: 160 } : {}}>
        {current.name}
      </Typography>
    </Breadcrumbs>
  );
}
