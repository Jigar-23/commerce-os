export interface PodcastItem {
  id: string;
  title: string;
  creator?: string;
  duration?: string;
  thumbnailUrl?: string;
  plays?: string;
  tags?: string[];
}

export interface PodcastShelf {
  id: number;
  category: string;
  slug: string;
  emoji: string;
  description: string;
  shows: PodcastItem[];
}

export const PODCAST_SHELVES_CONFIG: PodcastShelf[] = [
  {
    id: 1,
    category: "Lifestyle",
    slug: "lifestyle",
    emoji: "🌿",
    description: "Trending tech reviews, life hacks, and digital creator guides",
    shows: [
      {
        id: "ls-1",
        title: "GREAT FREE & PAY STREAMING SERVICE APPS FOR 2025",
        creator: "Tech & Living Hub",
        duration: "42 min",
        plays: "142K",
        tags: ["Streaming", "Apps 2025", "Lifestyle"]
      },
      {
        id: "ls-2",
        title: "How I Make $1,000/Month on YouTube",
        creator: "Creator Economy Unlocked",
        duration: "35 min",
        plays: "289K",
        tags: ["YouTube", "Side Hustle", "Passive Income"]
      }
    ]
  },
  {
    id: 2,
    category: "Marketing",
    slug: "marketing",
    emoji: "🚀",
    description: "Growth hacking, distribution strategies, and viral brand building",
    shows: [
      {
        id: "mkt-1",
        title: "How Varun Mayya built his distribution empire",
        creator: "Growth Insider",
        duration: "58 min",
        plays: "410K",
        tags: ["Varun Mayya", "Distribution", "Startup Growth"]
      },
      {
        id: "mkt-2",
        title: "WhatsApp Marketing 10X",
        creator: "Digital Funnel Masterclass",
        duration: "29 min",
        plays: "195K",
        tags: ["WhatsApp", "Conversational AI", "ROI"]
      }
    ]
  },
  {
    id: 3,
    category: "Business",
    slug: "business",
    emoji: "💼",
    description: "Founders, trade insights, renewable energy, and venture lessons",
    shows: [
      {
        id: "biz-1",
        title: "FO514 Raj Shamani",
        creator: "Figuring Out with Raj Shamani",
        duration: "1h 14min",
        plays: "850K",
        tags: ["Raj Shamani", "Venture Capital", "Founders"]
      },
      {
        id: "biz-2",
        title: "Solar & Hybrid Inverter Guide",
        creator: "Clean Energy Tech Talk",
        duration: "45 min",
        plays: "98K",
        tags: ["Solar", "Green Tech", "Manufacturing"]
      }
    ]
  },
  {
    id: 4,
    category: "Government & Politics",
    slug: "govt-politics",
    emoji: "🏛️",
    description: "Geopolitics, state affairs, intelligence, and defense analysis",
    shows: [
      {
        id: "gp-1",
        title: "Modi's Operation 360",
        creator: "National Policy Review",
        duration: "50 min",
        plays: "620K",
        tags: ["PM Modi", "Indian Foreign Policy", "Diplomacy"]
      },
      {
        id: "gp-2",
        title: "John Sipher | FO544 Raj Shamani",
        creator: "Figuring Out with Raj Shamani",
        duration: "1h 22min",
        plays: "1.2M",
        tags: ["John Sipher", "CIA", "Global Intelligence"]
      }
    ]
  },
  {
    id: 5,
    category: "Finances",
    slug: "finances",
    emoji: "💰",
    description: "Stock markets, credit smarts, wealth building, and behavioral finance",
    shows: [
      {
        id: "fin-1",
        title: "Trading Psychology",
        creator: "Market Mindset Podcast",
        duration: "38 min",
        plays: "310K",
        tags: ["Trading", "Risk Management", "Psychology"]
      },
      {
        id: "fin-2",
        title: "Indian Business Podcast",
        creator: "Wealth & Dalal Street",
        duration: "52 min",
        plays: "450K",
        tags: ["Macroeconomics", "India Growth", "Startups"]
      },
      {
        id: "fin-3",
        title: "Axis Airtel Credit Card",
        creator: "FinSmart India",
        duration: "24 min",
        plays: "215K",
        tags: ["Credit Cards", "Cashback", "Personal Finance"]
      }
    ]
  },
  {
    id: 6,
    category: "Science & Tech",
    slug: "science-tech",
    emoji: "🔬",
    description: "AI frontiers, space exploration, software engineering trends",
    shows: [
      {
        id: "st-1",
        title: "Truth About Coding Jobs in 2026",
        creator: "Tech Futures & AI",
        duration: "46 min",
        plays: "780K",
        tags: ["Software Jobs", "AI Coding", "Career 2026"]
      },
      {
        id: "st-2",
        title: "10 NASA Photos With Hidden Stories",
        creator: "Cosmic Mysteries",
        duration: "33 min",
        plays: "540K",
        tags: ["NASA", "Space Exploration", "Astronomy"]
      }
    ]
  },
  {
    id: 7,
    category: "Motivation",
    slug: "motivation",
    emoji: "🔥",
    description: "Personal mastery, resilience, goal setting, and YouTube success",
    shows: [
      {
        id: "mot-1",
        title: "Napoleon Hill Motivation",
        creator: "Mindset Champions",
        duration: "40 min",
        plays: "670K",
        tags: ["Napoleon Hill", "Think & Grow Rich", "Success"]
      },
      {
        id: "mot-2",
        title: "YouTube Growth Mastery",
        creator: "Content Machine",
        duration: "48 min",
        plays: "340K",
        tags: ["Growth Strategy", "Content Creator", "Viral"]
      }
    ]
  },
  {
    id: 8,
    category: "News",
    slug: "news",
    emoji: "📰",
    description: "Breaking global developments, space missions, and conflict updates",
    shows: [
      {
        id: "news-1",
        title: "ISRO Space World",
        creator: "Space & Defense Daily",
        duration: "28 min",
        plays: "510K",
        tags: ["ISRO", "Chandrayaan", "Space Tech"]
      },
      {
        id: "news-2",
        title: "Iran-US-Israel War",
        creator: "Global Geopolitics Watch",
        duration: "1h 05min",
        plays: "1.5M",
        tags: ["Middle East", "Geopolitics", "World News"]
      },
      {
        id: "news-3",
        title: "News Now India",
        creator: "India Pulse 24/7",
        duration: "30 min",
        plays: "420K",
        tags: ["India News", "Headlines", "Current Affairs"]
      }
    ]
  },
  {
    id: 9,
    category: "History",
    slug: "history",
    emoji: "📜",
    description: "Ancient civilizations, mythological sagas, and historical deep dives",
    shows: [
      {
        id: "hist-1",
        title: "The Odyssey & Trojan War | TRS",
        creator: "The Ranveer Show (TRS)",
        duration: "1h 18min",
        plays: "1.1M",
        tags: ["Trojan War", "Greek Mythology", "TRS"]
      },
      {
        id: "hist-2",
        title: "Egyptian Book of the Dead",
        creator: "Ancient Chronicles",
        duration: "55 min",
        plays: "430K",
        tags: ["Egypt", "Pyramids", "Ancient History"]
      },
      {
        id: "hist-3",
        title: "Historical Events Nitish Rajput",
        creator: "Nitish Rajput Voice",
        duration: "45 min",
        plays: "950K",
        tags: ["Nitish Rajput", "Indian History", "Documentary"]
      }
    ]
  },
  {
    id: 10,
    category: "Films & Suspense",
    slug: "films-suspense",
    emoji: "🎬",
    description: "Cinematic audio dramas, spooky lore, and pop culture roundtable",
    shows: [
      {
        id: "fs-1",
        title: "Radio Milan Audio Story",
        creator: "Radio Milan Nights",
        duration: "50 min",
        plays: "610K",
        tags: ["Audio Drama", "Suspense", "Radio Milan"]
      },
      {
        id: "fs-2",
        title: "Taranath Tantrik Bengali Horror",
        creator: "Creepy Bengali Tales",
        duration: "1h 02min",
        plays: "890K",
        tags: ["Taranath Tantrik", "Bengali Horror", "Occult"]
      },
      {
        id: "fs-3",
        title: "Men of Culture",
        creator: "Pop Culture Podcast",
        duration: "1h 15min",
        plays: "720K",
        tags: ["Cinema", "Pop Culture", "Reviews"]
      }
    ]
  },
  {
    id: 11,
    category: "Fiction",
    slug: "fiction",
    emoji: "📖",
    description: "Thrilling audiobooks, immersive stories, and mystery sagas",
    shows: [
      {
        id: "fic-1",
        title: "Bengali Audio Story Thriller Land",
        creator: "Thriller Land Audio",
        duration: "48 min",
        plays: "530K",
        tags: ["Bengali Thriller", "Audiobook", "Mystery"]
      },
      {
        id: "fic-2",
        title: "Sunday Suspense Mirchi Bangla",
        creator: "Radio Mirchi 98.3 FM",
        duration: "1h 10min",
        plays: "2.4M",
        tags: ["Sunday Suspense", "Mirchi Bangla", "Detective"]
      }
    ]
  },
  {
    id: 12,
    category: "Spirituality",
    slug: "spirituality",
    emoji: "🧘",
    description: "Sacred texts, paranormal encounters, and inner peace journeys",
    shows: [
      {
        id: "sp-1",
        title: "Real Paranormal & Horror Encounters",
        creator: "Supernatural India",
        duration: "52 min",
        plays: "810K",
        tags: ["Paranormal", "Real Stories", "Spiritual"]
      },
      {
        id: "sp-2",
        title: "Shri Hit Premanand Govind Sharan Ji",
        creator: "Vrindavan Satsang",
        duration: "35 min",
        plays: "1.8M",
        tags: ["Premanand Maharaj", "Vrindavan", "Bhakti"]
      },
      {
        id: "sp-3",
        title: "Bhagavad Geeta",
        creator: "Divine Life Teachings",
        duration: "1h 00min",
        plays: "1.2M",
        tags: ["Bhagavad Geeta", "Sanatan Dharma", "Wisdom"]
      }
    ]
  },
  {
    id: 13,
    category: "Politics",
    slug: "politics",
    emoji: "🗳️",
    description: "Leader interviews, political commentary, and long-form podcasts",
    shows: [
      {
        id: "pol-1",
        title: "The Diary Of A CEO",
        creator: "Steven Bartlett",
        duration: "1h 35min",
        plays: "3.5M",
        tags: ["Steven Bartlett", "DOAC", "Leadership"]
      },
      {
        id: "pol-2",
        title: "Yogi Adityanath Rajneeti",
        creator: "Political Uncut",
        duration: "44 min",
        plays: "910K",
        tags: ["UP Politics", "Yogi Adityanath", "Elections"]
      }
    ]
  },
  {
    id: 14,
    category: "Astrology",
    slug: "astrology",
    emoji: "🔮",
    description: "Planetary shifts, eclipse forecasts, and numerology insights",
    shows: [
      {
        id: "astro-1",
        title: "Shani Eclipse Predictions",
        creator: "Vedic Astrology Insights",
        duration: "36 min",
        plays: "490K",
        tags: ["Shani", "Saturn Transit", "Horoscope"]
      },
      {
        id: "astro-2",
        title: "Soul Number Connection",
        creator: "Numerology & Cosmos",
        duration: "29 min",
        plays: "320K",
        tags: ["Numerology", "Destiny", "Soul Code"]
      }
    ]
  },
  {
    id: 15,
    category: "Literature",
    slug: "literature",
    emoji: "📚",
    description: "Kavi sammelan, poetic recitations, and literary storytelling",
    shows: [
      {
        id: "lit-1",
        title: "Ashok Chakradhar Kumar Vishwas",
        creator: "Kavi Sammelan Stage",
        duration: "1h 08min",
        plays: "1.4M",
        tags: ["Kumar Vishwas", "Hindi Kavita", "Poetry"]
      },
      {
        id: "lit-2",
        title: "Radio Milan",
        creator: "Radio Milan Voice",
        duration: "40 min",
        plays: "380K",
        tags: ["Poetic Audio", "Classics", "Literature"]
      }
    ]
  },
  {
    id: 16,
    category: "Comedy",
    slug: "comedy",
    emoji: "🎭",
    description: "Hilarious banter, satire, stand-up snippets, and fake podcasts",
    shows: [
      {
        id: "com-1",
        title: "The Jay Thadeshwar Show",
        creator: "Jay Thadeshwar Comedy",
        duration: "32 min",
        plays: "520K",
        tags: ["Gujarati Humor", "Standup", "Jay Thadeshwar"]
      },
      {
        id: "com-2",
        title: "Fake Podcast Berozgaar Yuva",
        creator: "Youth Parody Lab",
        duration: "26 min",
        plays: "680K",
        tags: ["Satire", "Memes", "Berozgaar Yuva"]
      }
    ]
  },
  {
    id: 17,
    category: "Religion",
    slug: "religion",
    emoji: "🕉️",
    description: "Devotional chants, scripture recitations, and epic sagas",
    shows: [
      {
        id: "rel-1",
        title: "Sukhmani Sahib Path",
        creator: "Gurbani Kirtan",
        duration: "1h 15min",
        plays: "2.1M",
        tags: ["Sukhmani Sahib", "Gurbani", "Sikhism"]
      },
      {
        id: "rel-2",
        title: "Quran Recitation",
        creator: "Peaceful Tilawat",
        duration: "58 min",
        plays: "1.9M",
        tags: ["Quran", "Tilawat", "Islamic Chants"]
      },
      {
        id: "rel-3",
        title: "Shrimad Ramayan",
        creator: "Ramcharitmanas Audio",
        duration: "1h 30min",
        plays: "2.7M",
        tags: ["Ramayan", "Lord Ram", "Sanatan"]
      }
    ]
  },
  {
    id: 18,
    category: "Popular Shows",
    slug: "popular-shows",
    emoji: "⭐",
    description: "Top trending chart-busters & most requested podcasts across all genres",
    shows: [
      {
        id: "pop-1",
        title: "Sunday Suspense Mirchi Bangla",
        creator: "Radio Mirchi 98.3 FM",
        duration: "1h 10min",
        plays: "2.4M",
        tags: ["Top 1", "Trending", "Suspense"]
      },
      {
        id: "pop-2",
        title: "John Sipher | FO544 Raj Shamani",
        creator: "Figuring Out with Raj Shamani",
        duration: "1h 22min",
        plays: "1.2M",
        tags: ["Top 2", "Raj Shamani", "CIA"]
      },
      {
        id: "pop-3",
        title: "The Odyssey & Trojan War | TRS",
        creator: "The Ranveer Show (TRS)",
        duration: "1h 18min",
        plays: "1.1M",
        tags: ["Top 3", "TRS", "Mythology"]
      },
      {
        id: "pop-4",
        title: "Shri Hit Premanand Govind Sharan Ji",
        creator: "Vrindavan Satsang",
        duration: "35 min",
        plays: "1.8M",
        tags: ["Top 4", "Premanand Ji", "Bhakti"]
      }
    ]
  }
];
