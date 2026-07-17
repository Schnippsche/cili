# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CILI is a Spring Boot 4 hybrid file/media resource management system — combining features from Nextcloud (file management), ResourceSpace (digital asset management), and Jellyfin (media playback). Key features: folder/file CRUD with role-based permissions, HTML5 video playback with multi-language subtitles, FFmpeg-generated thumbnails, MySQL full-text search, and in-browser document viewing via LibreOffice → PDF → PDF.js.

**Deployment target**: Linux server (Ubuntu/Debian). Requires `ffmpeg`, `libreoffice-headless`, `openjdk-21-jre` on the server.

## Tech Stack

- **Backend**: Java 21, Spring Boot 4.0.6, Spring Security + JJWT 0.12.x, Spring Data JPA / Hibernate 6, Flyway, Lombok, MapStruct
- **Database**: MySQL 8 (utf8mb4, InnoDB, FULLTEXT indexes)
- **Frontend**: React 19 + TypeScript, Vite, Redux Toolkit, Axios
- **Packaging**: Single WAR via `frontend-maven-plugin` (Vite builds to `src/main/resources/static/`)

## Commands

```powershell
# Backend only (dev — skips frontend build)
.\mvnw.cmd spring-boot:run -Dspring.profiles.active=dev -Dmaven.skip=true

# Full build (frontend + backend → WAR)
.\mvnw.cmd clean package

# Tests only
.\mvnw.cmd test

# Frontend dev server (hot-reload, proxied to localhost:8080)
cd frontend && npm install && npm run dev

# Frontend build only
cd frontend && npm run build
```

## Architecture

### Backend Package Structure (`de.toengi.cili`)

```
config/          SecurityConfig, AsyncConfig, JwtConfig, FileStorageConfig, WebMvcConfig
security/        JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl, CiliUserDetails
  permission/    FolderPermissionEvaluator (PermissionEvaluator interface)
model/entity/    JPA entities
dto/             auth/, folder/, resource/, user/, group/, search/
repository/      JpaRepository interfaces
service/         Business logic
controller/      REST controllers
  admin/         AdminUserController, AdminGroupController
mapper/          MapStruct mappers
util/            FileNameUtils, MimeTypeUtils, RangeUtils
```

### Domain Model

```
User ───── UserGroupMembership ───── RightsGroup
                                          │
                                  GroupFolderPermission (canRead/Upload/Edit/Delete/Manage)
                                          │
                          Folder (adjacency list + materialized path `/1/4/12/`)
                            │
                         Resource ──── SubtitleTrack (languageCode + storedName)
                            │
                            ├── ResourceMetadata (description TEXT, tags)
                            └── Thumbnail (status: PENDING/DONE/FAILED, async via FFmpeg)
```

### Key Design Decisions

- **File storage**: UUID filenames in `{base-path}/resources/{first2chars}/{uuid}.ext` — base-path never stored in DB
- **Permissions**: additive model, per-folder per-group flags. ADMIN role bypasses all checks. `@PreAuthorize("hasPermission(#folderId, 'FOLDER', 'READ')")` on service methods
- **Subtitles**: auto-associated when `Video1.de.srt` uploaded into same folder as `Video1.mp4`
- **Thumbnails**: async Spring `@EventListener` + `@Async("thumbnailExecutor")` after upload
- **Video streaming**: custom `StreamController` with HTTP Range request support via Spring's `ResourceRegion`
- **Document viewing**: LibreOffice headless → PDF (cached), served inline, rendered client-side by PDF.js
- **Search**: MySQL FULLTEXT on `resource.original_name`, `resource_metadata.description/tags`, `folder.name`
- **JWT**: access token (15 min), refresh token stored as SHA-256 hash in `refresh_tokens` table (server-side revocable)

### Frontend Structure (`frontend/src/`)

```
api/        axiosClient.ts (interceptors for token refresh), auth.ts, ...
store/      store.ts, authSlice.ts
hooks/      useAuth.ts, ...
pages/      LoginPage.tsx, DashboardPage.tsx, FolderPage.tsx, ...
components/ layout/, folder/, resource/, viewer/, admin/
types/      api.ts (TypeScript types mirroring server DTOs)
```

### Flyway Migrations (`src/main/resources/db/migration/`)

| File | Content |
|---|---|
| V1 | users, rights_groups, user_group_memberships, refresh_tokens |
| V2 | folders, group_folder_permissions |
| V3 | resources, resource_metadata |
| V4 | subtitle_tracks, thumbnails |
| V5 | FULLTEXT indexes |

### File Storage Configuration

```properties
cili.storage.base-path=/var/cili/data
cili.storage.ffmpeg-path=/usr/bin/ffmpeg
cili.storage.ffprobe-path=/usr/bin/ffprobe
cili.storage.libreoffice-path=/usr/bin/soffice
```

Default admin login: `admin` / `admin` (set in V1 migration — change immediately in production).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
