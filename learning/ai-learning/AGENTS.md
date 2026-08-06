# AGENTS

<skills_system priority="1">

## Available Skills

<!-- SKILLS_TABLE_START -->
<usage>
When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

How to use skills:
- Invoke: `npx openskills read <skill-name>` (run in your shell)
  - For multiple: `npx openskills read skill-one,skill-two`
- The skill content will load with detailed instructions on how to complete the task
- Base directory provided in output for resolving bundled resources (references/, scripts/, assets/)

Usage notes:
- Only use skills listed in <available_skills> below
- Do not invoke a skill that is already loaded in your context
- Each skill invocation is stateless
</usage>

<available_skills>

<skill>
<name>agenthero-ai</name>
<description>AgentHero AI - Hierarchical multi-agent orchestration system with PM coordination, file-based state management, and interactive menu interface. Use when managing complex multi-agent workflows, coordinating parallel sub-agents, or organizing large project tasks with multiple specialists. All created agents use aghero- prefix.</description>
<location>project</location>
</skill>

<skill>
<name>changelog-manager</name>
<description>Update project changelog with uncommitted changes, synchronize package versions, and create version releases with automatic commit, conditional git tags, GitHub Releases, and push</description>
<location>project</location>
</skill>

<skill>
<name>cli-modern-tools</name>
<description>Auto-suggest modern CLI tool alternatives (bat, eza, fd, ripgrep) for faster, more efficient command-line operations with 50%+ speed improvements</description>
<location>project</location>
</skill>

<skill>
<name>colored-output</name>
<description>Centralized colored output formatter for all skills, agents, and commands with ANSI escape codes</description>
<location>project</location>
</skill>

<skill>
<name>docx</name>
<description>"Comprehensive document creation, editing, and analysis with support for tracked changes, comments, formatting preservation, and text extraction. When Claude needs to work with professional documents (.docx files) for: (1) Creating new documents, (2) Modifying or editing content, (3) Working with tracked changes, (4) Adding comments, or any other document tasks"</description>
<location>project</location>
</skill>

<skill>
<name>image-fetcher</name>
<description>Fetch and download images from the internet in various formats (JPG, PNG, GIF, WebP, BMP, SVG, etc.). Use when users ask to download images, fetch images from URLs, save images from the web, or get images for embedding in documents or chats. Supports single and batch downloads with automatic format detection.</description>
<location>project</location>
</skill>

<skill>
<name>lark-agent</name>
<description>|</description>
<location>project</location>
</skill>

<skill>
<name>lark-agent-simple</name>
<description>|</description>
<location>project</location>
</skill>

<skill>
<name>log-analysis-tools</name>
<description>Fast log file analysis with lnav, multi-framework support (Laravel, CodeIgniter, React, Next.js), automatic log pruning, and 70-80% token savings vs reading entire logs</description>
<location>project</location>
</skill>

<skill>
<name>markdown-helper</name>
<description>Token-efficient markdown parsing, editing, and diagram generation using native CLI tools (Windows/Mac/Linux compatible)</description>
<location>project</location>
</skill>

<skill>
<name>pdf</name>
<description>Comprehensive PDF manipulation toolkit for extracting text and tables, creating new PDFs, merging/splitting documents, and handling forms. When Claude needs to fill in a PDF form or programmatically process, generate, or analyze PDF documents at scale.</description>
<location>project</location>
</skill>

<skill>
<name>pptx</name>
<description>"Presentation creation, editing, and analysis. When Claude needs to work with presentations (.pptx files) for: (1) Creating new presentations, (2) Modifying or editing content, (3) Working with layouts, (4) Adding comments or speaker notes, or any other presentation tasks"</description>
<location>project</location>
</skill>

<skill>
<name>sap-article-generator-v2</name>
<description>Generate comprehensive, fact-checked SAP technical articles and configuration guides with embedded images, flowcharts, and references. Use when users request SAP documentation, how-to guides, configuration tutorials, process explanations, or technical articles on SAP topics (ECC, S/4HANA, modules like SD, MM, FI, PP, ABAP, OData APIs, archiving, etc.). Creates professional Word documents with proper formatting and web-sourced visual aids with built-in image downloading.</description>
<location>project</location>
</skill>

<skill>
<name>skill-creator</name>
<description>Guide for creating effective skills. This skill should be used when users want to create a new skill (or update an existing skill) that extends Claude's capabilities with specialized knowledge, workflows, or tool integrations.</description>
<location>project</location>
</skill>

<skill>
<name>skill-manager</name>
<description>Native Python-based skill management for enabling/disabling skills, configuring permissions, and managing settings.local.json</description>
<location>project</location>
</skill>

<skill>
<name>sql-cli</name>
<description>Token-efficient MySQL/PostgreSQL operations using mycli and native CLI tools (Windows/Mac/Linux compatible). Replaces Artisan Tinker for database queries with 87% token savings.</description>
<location>project</location>
</skill>

<skill>
<name>template-skill</name>
<description>Replace with description of the skill and when Claude should use it.</description>
<location>project</location>
</skill>

<skill>
<name>time-helper</name>
<description>Get current time, convert timezones, and perform time calculations using native PHP (Windows/Mac/Linux compatible)</description>
<location>project</location>
</skill>

<skill>
<name>webapp-testing</name>
<description>Toolkit for interacting with and testing local web applications using Playwright. Supports verifying frontend functionality, debugging UI behavior, capturing browser screenshots, and viewing browser logs.</description>
<location>project</location>
</skill>

<skill>
<name>xlsx</name>
<description>"Comprehensive spreadsheet creation, editing, and analysis with support for formulas, formatting, data analysis, and visualization. When Claude needs to work with spreadsheets (.xlsx, .xlsm, .csv, .tsv, etc) for: (1) Creating new spreadsheets with formulas and formatting, (2) Reading or analyzing data, (3) Modify existing spreadsheets while preserving formulas, (4) Data analysis and visualization in spreadsheets, or (5) Recalculating formulas"</description>
<location>project</location>
</skill>

</available_skills>
<!-- SKILLS_TABLE_END -->

</skills_system>
