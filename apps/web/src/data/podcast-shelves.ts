export interface PodcastShow {
  id: string;
  title: string;
  creator: string;
  duration?: string;
  plays?: string;
  tags?: string[];
}

export interface PodcastShelf {
  id: number;
  category: string;
  description: string;
  emoji: string;
  shows: PodcastShow[];
}

export const PODCAST_SHELVES: PodcastShelf[] = [
  {
    id: 1,
    category: 'Lifestyle & Wellness',
    description: 'Mindful living, fitness science, and daily habit optimization',
    emoji: '🌿',
    shows: [
      { id: 'lw-1', title: 'The Daily Optimizer', creator: 'Dr. Sarah Jenkins', duration: '28 min', plays: '340K', tags: ['Habits', 'Wellness'] },
      { id: 'lw-2', title: 'Longevity Protocols', creator: 'Marcus Vance', duration: '45 min', plays: '520K', tags: ['Longevity', 'Biohacking'] },
      { id: 'lw-3', title: 'Sleep & Recovery Lab', creator: 'Elena Rostova', duration: '32 min', plays: '210K', tags: ['Sleep', 'Recovery'] },
    ],
  },
  {
    id: 2,
    category: 'Marketing & Brand Strategy',
    description: 'Growth mechanics, viral distribution, and category design',
    emoji: '📈',
    shows: [
      { id: 'mb-1', title: 'Category Pirates Uncut', creator: 'Eddie Yoon & Christopher Lochhead', duration: '50 min', plays: '410K', tags: ['Strategy', 'Category'] },
      { id: 'mb-2', title: 'Zero to One Million CAC', creator: 'Rachel Torres', duration: '38 min', plays: '620K', tags: ['Growth', 'D2C'] },
      { id: 'mb-3', title: 'The Brand Moat', creator: 'Devon Wright', duration: '42 min', plays: '290K', tags: ['Brand', 'Positioning'] },
    ],
  },
  {
    id: 3,
    category: 'Commerce & Quick Logistics',
    description: 'Dark store ergonomics, OSRM routing algorithms, and cold-chain scale',
    emoji: '⚡',
    shows: [
      { id: 'cq-1', title: '10-Minute Fulfillment Deep Dive', creator: 'Commerce OS Engineering', duration: '35 min', plays: '890K', tags: ['SupplyChain', 'MicroHubs'] },
      { id: 'cq-2', title: 'Last-Mile Unit Economics', creator: 'Arjun Mehta', duration: '44 min', plays: '480K', tags: ['Logistics', 'Riders'] },
      { id: 'cq-3', title: 'Cold-Chain Telemetry', creator: 'Dr. Vikram Sethi', duration: '29 min', plays: '310K', tags: ['Pharma', 'Temperature'] },
    ],
  },
  {
    id: 4,
    category: 'Technology & Distributed Systems',
    description: 'PostgreSQL concurrency, optimistic locking, and event outboxes',
    emoji: '💻',
    shows: [
      { id: 'ts-1', title: 'ACID Transactions at Scale', creator: 'Database Masters', duration: '55 min', plays: '740K', tags: ['Postgres', 'Concurrency'] },
      { id: 'ts-2', title: 'Fail-Closed Production Architectures', creator: 'System Architects Pod', duration: '48 min', plays: '610K', tags: ['Reliability', 'SRE'] },
      { id: 'ts-3', title: 'Real-Time Telemetry & SSE', creator: 'Frontline Engineers', duration: '33 min', plays: '380K', tags: ['WebSockets', 'SSE'] },
    ],
  },
  {
    id: 5,
    category: 'History & Civilizations',
    description: 'Decisive turning points, trade routes, and archaeological breakthroughs',
    emoji: '🏛️',
    shows: [
      { id: 'hc-1', title: 'Silk Road Mercantile Networks', creator: 'Prof. Alistair Grant', duration: '62 min', plays: '530K', tags: ['History', 'Trade'] },
      { id: 'hc-2', title: 'Industrial Epochs', creator: 'Hannah Moore', duration: '41 min', plays: '410K', tags: ['Industry', 'Economy'] },
    ],
  },
  {
    id: 6,
    category: 'Comedy & Satire',
    description: 'Sharp observational humor and tech boardroom parodies',
    emoji: '🎭',
    shows: [
      { id: 'cs-1', title: 'Standup Sprint Review', creator: 'The Agile Comedians', duration: '25 min', plays: '820K', tags: ['Humor', 'Tech'] },
      { id: 'cs-2', title: 'Venture Capital Anonymous', creator: 'Sam & Maya', duration: '34 min', plays: '670K', tags: ['Satire', 'Startups'] },
    ],
  },
];
