// =============================================================================
//                 BEREZA ROLE PLAY — ГОЛОВНИЙ ФАЙЛ МОДУ (v2.0)
// =============================================================================
//  Що є в моді:
//    * реєстрація та вхід (акаунти зберігаються в SQLite: scriptfiles/bereza.db)
//    * вибір статі та скіну при реєстрації (3 бомжі для чоловіків і 3 для жінок)
//    * оренда мопедів (Faggio) на вокзалі
//    * 15 початкових квестів від бомжа Віталіка
//    * базові RP-команди: /me /do /try /b, локальний чат
//
//  Потрібні плагіни: streamer, Pawn.CMD (MySQL більше не потрібен).
//  ВАЖЛИВО: файл має бути в кодуванні Windows-1251, інакше в грі будуть "кракозябри".
//  Керування: ALT (або команда /d для телефонів) — дія біля NPC/точок.
// =============================================================================

#include <a_samp>
#include <Pawn.CMD>
#include <streamer>

#pragma warning disable 239

// =============================================================================
// 1. НАЛАШТУВАННЯ, КОЛЬОРИ, ДІАЛОГИ
// =============================================================================
#define SERVER_NAME         "Bereza RP"
#define DB_FILE             "bereza.db"

#define START_MONEY         300
#define MAX_LOGIN_TRIES     3
#define PASS_MIN_LEN        6
#define PASS_MAX_LEN        32

#define COLOR_GREEN         0x33AA33FF
#define COLOR_WHITE         0xFFFFFFFF
#define COLOR_YELLOW        0xFFFF00FF
#define COLOR_GREY          0xAFAFAFFF
#define COLOR_RED           0xFF3333FF
#define COLOR_SMS           0x33CCFFFF
#define COLOR_PURPLE        0xC2A2DAFF
#define COLOR_ORANGE        0xFF9900FF

enum
{
    DIALOG_NONE,
    DIALOG_LOGIN,
    DIALOG_REGISTER,
    DIALOG_REGISTER_CONFIRM,
    DIALOG_SEX,
    DIALOG_SKIN,
    DIALOG_MENU,
    DIALOG_INFO,
    DIALOG_RENT,
    DIALOG_QUEST_OFFER,
    DIALOG_Q_RECYCLE,
    DIALOG_Q_QUIZ,
    DIALOG_Q_LOTTERY,
    DIALOG_Q_AGE,
    DIALOG_Q_INTERVIEW,
    DIALOG_Q_FINAL,
    DIALOG_Q_FINAL_SKIN
}

// --- Спавн (вокзал Юніті, Лос-Сантос) ---
#define SPAWN_X             1759.0186
#define SPAWN_Y             -1898.1260
#define SPAWN_Z             13.5622
#define SPAWN_A             266.4503

// --- Мерія ---
#define MAYOR_ENT_X         1481.0542
#define MAYOR_ENT_Y         -1771.8157
#define MAYOR_ENT_Z         18.7958
#define MAYOR_ENT_A         0.0
#define MAYOR_INT_X         389.8938
#define MAYOR_INT_Y         173.8116
#define MAYOR_INT_Z         1008.3828
#define MAYOR_INT_A         90.0
#define MAYOR_INT_ID        3
#define MAYOR_VW            100

// --- Скіни при реєстрації (бомжі). ID можна замінити на свої ---
new const MaleSkins[3]   = {78, 79, 137};
new const FemaleSkins[3] = {77, 31, 157};
new const MaleSkinNames[3][]   = {"Бомж-філософ", "Бомж-романтик", "Бомж-бувалець"};
new const FemaleSkinNames[3][] = {"Бабця з торбою", "Дама з картонки", "Королева вокзалу"};

// --- Скіни, які можна обрати у фінальному квесті ---
new const FinalMaleSkins[3]   = {23, 50, 147};
new const FinalFemaleSkins[3] = {56, 131, 150};

// =============================================================================
// 2. ДАНІ ГРАВЦЯ
// =============================================================================
enum
{
    ACT_NONE,
    ACT_SING,
    ACT_FISH,
    ACT_PHOTO,
    ACT_SEARCH
}

enum E_PLAYER
{
    pID,
    pHash[65],
    pSalt[17],
    pSex,               // 1 - чоловік, 2 - жінка
    pSkin,
    pMoney,
    pAge,
    pPassport,
    pQuest,             // номер поточного квесту (0..14), 15 = всі виконані
    pQuestStep,
    pQuestActive,
    // --- тимчасові (не зберігаються) ---
    bool:pLogged,
    bool:pAccountExists,
    bool:pAuthShown,
    bool:pRegistering,
    pLoginTries,
    pSkinIndex,
    pTempPass[PASS_MAX_LEN + 1],
    pRentVeh,
    pRentLeft,          // секунд до кінця оренди
    pQuestTimer,        // >0 - іде відлік, 0 - немає, -1 - час вийшов
    pQuestExtra,        // бали/чайові/зароблені монети
    pCheckpoint,
    pAction,
    pActionTime,
    pActionArg,
    Float:pActX,
    Float:pActY,
    Float:pActZ
}
new PlayerInfo[MAX_PLAYERS][E_PLAYER];

new DB:Database;
new RentOwner[MAX_VEHICLES];
new ServerTimer;

// =============================================================================
// 3. ТОЧКИ НА КАРТІ (координати можна підправити командою /mypos)
// =============================================================================
enum E_POINT
{
    Float:ptX,
    Float:ptY,
    Float:ptZ,
    Float:ptSize
}

enum
{
    PT_VITALIK,
    PT_RENT,
    PT_RECYCLE,
    PT_SHAWARMA,
    PT_GROVE,
    PT_STAGE,
    PT_PIER,
    PT_WATER,
    PT_LOTTERY,
    PT_MAYOR_DOOR,
    PT_MAYOR_DESK,
    PT_PHOTO,
    PT_BURGER,
    PT_DELIVERY_1,
    PT_DELIVERY_2,
    PT_DELIVERY_3
}
#define PT_NONE     (-1)
#define PT_DUMPSTER (100)

new const Points[][E_POINT] =
{
    {1808.4893, -1900.0646, 13.5622, 2.0},   // Віталік
    {1805.5000, -1903.5000, 13.5622, 2.0},   // Прокат мопедів
    {1831.0000, -1846.0000, 13.5781, 2.0},   // Пункт прийому склотари
    {2105.3600, -1806.4700, 13.5547, 2.0},   // Шаурма в Ашота (Айдлвуд)
    {2486.5400, -1666.1500, 13.3438, 3.0},   // Гроув-стріт
    {1479.0000, -1662.0000, 14.0469, 3.0},   // Сцена на площі Першинг
    {836.5000, -2045.0000, 12.8672, 3.0},    // Кінець пірсу Санта-Марія
    {790.0000, -1990.0000, 0.0000, 8.0},     // Океан біля пірсу
    {1352.3780, -1759.2532, 13.5078, 2.0},   // Магазин 24/7 (Комерс)
    {MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z, 2.0}, // Вхід у мерію
    {375.0000, 173.8000, 1008.3828, 1.5},    // Віконце в мерії
    {1128.7321, -1270.5219, 13.5468, 2.0},   // Фотоательє (Маркет)
    {810.4800, -1616.2100, 13.5469, 2.0},    // Burger Shot (Марина)
    {1178.0000, -1323.5000, 14.1000, 2.5},   // Лікарня All Saints
    {1544.0000, -1675.5000, 13.5569, 2.5},   // Поліція ЛС
    {2229.9000, -1721.4000, 13.5625, 2.5}    // Спортзал Гантон
};

#define RENT_SPAWN_X        1800.0000
#define RENT_SPAWN_Y        -1908.0000
#define RENT_SPAWN_Z        13.4000
#define RENT_SPAWN_A        90.0
#define RENT_MODEL          462     // Faggio

enum E_TARIFF { trMinutes, trPrice }
new const RentTariffs[][E_TARIFF] =
{
    {15, 99},
    {30, 179},
    {60, 299}
};

#define MAX_BOTTLES 5
new const Float:BottlePos[MAX_BOTTLES][3] =
{
    {1765.0, -1915.0, 13.55},
    {1790.0, -1925.0, 13.40},
    {1745.0, -1930.0, 13.55},
    {1815.0, -1915.0, 13.40},
    {1775.0, -1882.0, 13.55}
};
new STREAMER_TAG_PICKUP:BottlePickup[MAX_PLAYERS][MAX_BOTTLES];
new STREAMER_TAG_MAP_ICON:BottleIcon[MAX_PLAYERS][MAX_BOTTLES];

#define MAX_DUMPSTERS 4
new const Float:DumpsterPos[MAX_DUMPSTERS][4] =
{
    {1770.0, -1925.0, 13.40, 0.0},
    {1795.0, -1930.0, 13.40, 0.0},
    {1820.0, -1930.0, 13.40, 90.0},
    {1745.0, -1925.0, 13.55, 90.0}
};

// =============================================================================
// 4. КВЕСТИ
// =============================================================================
#define MAX_QUESTS 15
enum
{
    Q_BOTTLES,
    Q_RECYCLE,
    Q_QUIZ,
    Q_MOPED,
    Q_SHAWARMA,
    Q_MUSIC,
    Q_FISH,
    Q_SWIM,
    Q_DUMPSTER,
    Q_LOTTERY,
    Q_MAYOR,
    Q_PHOTO,
    Q_INTERVIEW,
    Q_SHIFT,
    Q_FINAL
}

#define SHAWARMA_TIME   75
#define SHAWARMA_TIP    40      // якщо залишилось >= 40 сек — чайові
#define SHIFT_TIME      150
#define SING_TIME       30
#define FISH_TIME       6
#define PHOTO_TIME      10
#define SEARCH_TIME     3

new const QuestName[MAX_QUESTS][] =
{
    "Сніданок чемпіона",
    "Дзвінка пляшка",
    "Закони вулиці",
    "Колеса свободи",
    "Гаряча шаурма",
    "Вуличний музикант",
    "Золота рибка",
    "Королівське купання",
    "Сміттєвий барон",
    "Щасливий квиток",
    "Галина Петрівна",
    "Фото 3х4",
    "Співбесіда",
    "Перша зміна",
    "Секрет Віталіка"
};

new const QuestGoal[MAX_QUESTS][] =
{
    "Зберіть 5 порожніх пляшок біля вокзалу (значки на радарі).",
    "Здайте пляшки в пункт прийому біля магазину 24/7 (червоний маркер).",
    "Правильно дайте відповіді на 5 питань Віталіка.",
    "Орендуйте мопед на вокзалі (ALT біля прокату або /rent).",
    "Заберіть шаурму в Ашота та доставте на Гроув-стріт за 75 секунд.",
    "Дайте 30-секундний концерт на площі Першинг (ALT або /sing).",
    "Зловіть 3 рибини на пірсі Санта-Марія (ALT або /fish).",
    "Скупайтесь в океані біля пірсу (запливіть у маркер).",
    "Обшукайте 4 смітники біля вокзалу (ALT або /search).",
    "Перевірте лотерейний квиток у магазині 24/7 на Комерс.",
    "Зверніться до віконця в мерії.",
    "Сфотографуйтесь в ательє (стійте нерухомо 10 сек) і поверніться в мерію.",
    "Пройдіть співбесіду в Burger Shot на Марині.",
    "Доставте 3 замовлення: лікарня, поліція, спортзал.",
    "Поверніться до Віталіка на вокзал."
};

new const QuestStory[MAX_QUESTS][] =
{
    "Віталік: «О, новенький! Бачу по очах — життя тебе теж не жаліло. Я Віталік, король цього вокзалу.\nХочеш піднятися з самого дна? Почнемо з азів: навколо вокзалу валяються порожні пляшки.\nЗбери 5 штук — це буде твій перший капітал!»",
    "SMS від Віталіка: «Чув, ти вже зібрав пляшки. Красава!\nНеси їх Борису в пункт прийому «Дзвінка пляшка» біля магазину 24/7.\nТільки тримай вухо гостро — Борис ще той жук.»",
    "SMS від Віталіка: «Гроші — це добре, але в місті треба знати закони вулиці.\nЗараз я поставлю тобі кілька питань. Помилишся — почнемо спочатку,\nбо незнання законів не звільняє від відповідальності!»",
    "SMS від Віталіка: «Ногами далеко не зайдеш. Біля мене на вокзалі є прокат мопедів.\nОрендуй собі залізного коня — ціни народні, я перевіряв!»",
    "SMS від Віталіка: «Мій друг Ашот продає найкращу шаурму в Айдлвуді, і йому потрібен кур'єр.\nЗабери замовлення і доправ його на Гроув-стріт, поки воно гаряче.\nХолодну шаурму там не пробачають...»",
    "SMS від Віталіка: «Знаєш, як я заробив свій перший мільйон? Я теж не знаю.\nА от перші сто гривень я заробив піснею! Йди на площу Першинг і влаштуй концерт.\nПубліка там щедра, якщо не втечеш зі сцени.»",
    "SMS від Віталіка: «Сьогодні в меню риба! Йди на пірс Санта-Марія і злови 3 рибини.\nЯ колись спіймав там золоту рибку, але вона попросила адвоката...»",
    "SMS від Віталіка: «Слухай, друже, скажу як є: від тебе тхне гірше, ніж від мене. А це рекорд!\nСкупайся в океані біля пірсу, бо далі тебе навіть у смітник не пустять.»",
    "SMS від Віталіка: «Справжній бомж знає: смітник — це скриня зі скарбами.\nБіля вокзалу стоять 4 смітники. Обшукай кожен — я нутром чую, там щось цінне!»",
    "SMS від Віталіка: «ЩО?! Лотерейний квиток?! Швидше в магазин 24/7 на Комерс, перевір його!\nЯкщо виграєш — пам'ятай, хто тебе туди відправив...»",
    "SMS від Віталіка: «З такими грошима треба легалізуватися. Йди в мерію й отримай паспорт.\nПопереджаю: там працює Галина Петрівна. Її бояться навіть бомжі.»",
    "Галина Петрівна: «Паспорт? А фото 3х4 є? Нема? То й паспорта нема!\nФотоательє на Маркеті. І не кліпайте там, бо будете 10 років\nз закритими очима в паспорті ходити. Наступний!»",
    "SMS від Віталіка: «Тепер ти людина з паспортом! Час на справжню роботу.\nУ Burger Shot на Марині шукають працівника. Скажи, що ти від мене —\nменеджер мені винен ще з 2004 року.»",
    "Менеджер Burger Shot: «Вітаю в команді! Перша зміна — доставка.\nТри замовлення, по 2.5 хвилини на кожне. Встигнеш — будуть чайові.\nМопеда нема? Ну, тоді бігай швидко!»",
    "SMS від Віталіка: «Друже, ти пройшов довгий шлях. Повертайся на вокзал,\nмені треба тобі дещо розповісти. Важливе. Цього разу без жартів.»"
};

// --- Питання іспиту "Закони вулиці" ---
enum E_QUIZ { qzQuestion[64], qzAnswers[300], qzRight }
new const QuizData[][E_QUIZ] =
{
    {"Що таке RP (Role Play)?", "Гра за роль: живеш життям свого персонажа\nРежим, де всіх можна вбивати\nНазва нового енергетика", 0},
    {"Що таке DM?", "Дуже Мила фраза для привітання\nВбивство без RP-причини (заборонено)\nДомашній Мопед", 1},
    {"Що таке MetaGaming (MG)?", "Гра в метро\nМега-Гроші за донат\nІнформація з реального життя в грі (заборонено)", 2},
    {"Тебе збила машина. Твої дії?", "Відіграю травму через /me і чекаю медиків\nВстаю і йду, ніби нічого не сталось\nДістаю базуку", 0},
    {"Що таке PowerGaming (PG)?", "Гра на потужному комп'ютері\nНереальні дії (підняти авто однією рукою)\nРобота на електростанції", 1}
};

// --- Питання співбесіди в Burger Shot ---
enum E_INTERVIEW { ivQuestion[64], ivAnswers[300], ivScore[3] }
new const InterviewData[][E_INTERVIEW] =
{
    {"Чому ви хочете працювати в нас?", "Хочу зробити кар'єру\nКажуть, тут безкоштовна картопля\nМене прислав Віталік", {2, 1, 3}},
    {"Ваша найсильніша сторона?", "Пунктуальність\nВмію спати стоячи\nЯ знайшов виграшний квиток у смітнику", {2, 1, 2}},
    {"Клієнт кричить: бургер холодний!", "Вибачитись і замінити бургер\nСказати, що це гаспачо-бургер\nПокликати Віталіка", {3, 1, 0}}
};

new const DumpsterLoot[][] =
{
    "надкушений бургер. Віталік би оцінив.",
    "золотий зуб! Ломбард дав за нього 80 грн.",
    "журнал «Мопеди України» 1998 року. Безцінно.",
    "сердитого кота. Він подряпав вас і втік.",
    "стару куртку, а в кишені — 45 грн!"
};

new const DeliveryNames[3][] =
{
    "медсестрі Олені в лікарні All Saints",
    "черговому поліцейському (без цибулі!)",
    "тренеру в спортзалі Гантон (подвійний бургер, тсс!)"
};

new const VitalikPhrases[][] =
{
    "Віталік: «Життя — як шаурма: ніколи не знаєш, що всередині.»",
    "Віталік: «Колись і в мене був мопед. Золотий...»",
    "Віталік: «Не бійся впасти на дно. Тут, принаймні, стабільно.»",
    "Віталік: «Гроші не пахнуть. А от деякі люди — дуже.»",
    "Віталік: «Голуби на вокзалі знають більше, ніж поліція.»"
};

// =============================================================================
// 5. ДОПОМІЖНІ ФУНКЦІЇ
// =============================================================================
stock GetName(playerid)
{
    new name[MAX_PLAYER_NAME];
    GetPlayerName(playerid, name, sizeof(name));
    return name;
}

stock GetRPName(playerid)
{
    new name[MAX_PLAYER_NAME];
    GetPlayerName(playerid, name, sizeof(name));
    for(new i = 0; name[i] != '\0'; i++)
    {
        if(name[i] == '_') name[i] = ' ';
    }
    return name;
}

stock GiveMoney(playerid, amount)
{
    PlayerInfo[playerid][pMoney] += amount;
    if(PlayerInfo[playerid][pMoney] < 0) PlayerInfo[playerid][pMoney] = 0;
    ResetPlayerMoney(playerid);
    GivePlayerMoney(playerid, PlayerInfo[playerid][pMoney]);
}

stock CountBits(value)
{
    new count = 0;
    while(value)
    {
        count += value & 1;
        value >>= 1;
    }
    return count;
}

stock IsNearPoint(playerid, pt, Float:range)
{
    return IsPlayerInRangeOfPoint(playerid, range, Points[pt][ptX], Points[pt][ptY], Points[pt][ptZ]);
}

stock SendLocalMessage(playerid, Float:range, color, const message[])
{
    new Float:x, Float:y, Float:z;
    GetPlayerPos(playerid, x, y, z);
    new vw = GetPlayerVirtualWorld(playerid);
    for(new i = 0, j = GetPlayerPoolSize(); i <= j; i++)
    {
        if(!IsPlayerConnected(i) || !PlayerInfo[i][pLogged]) continue;
        if(GetPlayerVirtualWorld(i) != vw) continue;
        if(IsPlayerInRangeOfPoint(i, range, x, y, z)) SendClientMessage(i, color, message);
    }
}

stock ShowInfo(playerid, caption[], text[])
{
    ShowPlayerDialog(playerid, DIALOG_INFO, DIALOG_STYLE_MSGBOX, caption, text, "Добре", "");
}

forward DelayedKick(playerid);
public DelayedKick(playerid)
{
    Kick(playerid);
    return 1;
}

stock KickWithDelay(playerid)
{
    SetTimerEx("DelayedKick", 500, false, "i", playerid);
}

stock PreloadAnimations(playerid)
{
    ApplyAnimation(playerid, "DANCING", "null", 0.0, 0, 0, 0, 0, 0);
    ApplyAnimation(playerid, "SAMP", "null", 0.0, 0, 0, 0, 0, 0);
    ApplyAnimation(playerid, "BOMBER", "null", 0.0, 0, 0, 0, 0, 0);
    ApplyAnimation(playerid, "ON_LOOKERS", "null", 0.0, 0, 0, 0, 0, 0);
}

// =============================================================================
// 6. АКАУНТИ (SQLite)
// =============================================================================
stock DB_Init()
{
    Database = db_open(DB_FILE);
    db_free_result(db_query(Database,
        "CREATE TABLE IF NOT EXISTS accounts (\
        id INTEGER PRIMARY KEY AUTOINCREMENT, \
        name TEXT NOT NULL UNIQUE COLLATE NOCASE, \
        hash TEXT NOT NULL, salt TEXT NOT NULL, \
        sex INTEGER DEFAULT 1, skin INTEGER DEFAULT 78, money INTEGER DEFAULT 0, \
        age INTEGER DEFAULT 0, passport INTEGER DEFAULT 0, \
        quest INTEGER DEFAULT 0, quest_step INTEGER DEFAULT 0, quest_active INTEGER DEFAULT 0, \
        reg_date INTEGER DEFAULT 0, last_login INTEGER DEFAULT 0)"));
}

stock GenerateSalt(dest[], len)
{
    new const chars[] = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    for(new i = 0; i < len - 1; i++)
    {
        dest[i] = chars[random(sizeof(chars) - 1)];
    }
    dest[len - 1] = '\0';
}

// Нікнейми в SA-MP можуть містити лише [a-zA-Z0-9_[]()$@.=], тому лапок у запиті не буде.
stock Account_Check(playerid)
{
    new query[128];
    format(query, sizeof(query), "SELECT * FROM accounts WHERE name = '%s' LIMIT 1", GetName(playerid));
    new DBResult:result = db_query(Database, query);

    PlayerInfo[playerid][pAccountExists] = false;
    if(result)
    {
        if(db_num_rows(result) > 0)
        {
            PlayerInfo[playerid][pAccountExists] = true;
            PlayerInfo[playerid][pID]          = db_get_field_assoc_int(result, "id");
            db_get_field_assoc(result, "hash", PlayerInfo[playerid][pHash], 65);
            db_get_field_assoc(result, "salt", PlayerInfo[playerid][pSalt], 17);
            PlayerInfo[playerid][pSex]         = db_get_field_assoc_int(result, "sex");
            PlayerInfo[playerid][pSkin]        = db_get_field_assoc_int(result, "skin");
            PlayerInfo[playerid][pMoney]       = db_get_field_assoc_int(result, "money");
            PlayerInfo[playerid][pAge]         = db_get_field_assoc_int(result, "age");
            PlayerInfo[playerid][pPassport]    = db_get_field_assoc_int(result, "passport");
            PlayerInfo[playerid][pQuest]       = db_get_field_assoc_int(result, "quest");
            PlayerInfo[playerid][pQuestStep]   = db_get_field_assoc_int(result, "quest_step");
            PlayerInfo[playerid][pQuestActive] = db_get_field_assoc_int(result, "quest_active");
        }
        db_free_result(result);
    }
}

stock Account_Create(playerid)
{
    GenerateSalt(PlayerInfo[playerid][pSalt], 17);
    SHA256_PassHash(PlayerInfo[playerid][pTempPass], PlayerInfo[playerid][pSalt], PlayerInfo[playerid][pHash], 65);
    PlayerInfo[playerid][pTempPass][0] = '\0';

    new query[512];
    format(query, sizeof(query),
        "INSERT INTO accounts (name, hash, salt, sex, skin, money, reg_date, last_login) VALUES ('%s', '%s', '%s', %d, %d, %d, %d, %d)",
        GetName(playerid), PlayerInfo[playerid][pHash], PlayerInfo[playerid][pSalt],
        PlayerInfo[playerid][pSex], PlayerInfo[playerid][pSkin], PlayerInfo[playerid][pMoney], gettime(), gettime());
    db_free_result(db_query(Database, query));

    new DBResult:result = db_query(Database, "SELECT last_insert_rowid()");
    if(result)
    {
        PlayerInfo[playerid][pID] = db_get_field_int(result, 0);
        db_free_result(result);
    }
    PlayerInfo[playerid][pAccountExists] = true;
}

stock Account_Save(playerid)
{
    if(!PlayerInfo[playerid][pLogged] || PlayerInfo[playerid][pID] == 0) return 0;

    new query[400];
    format(query, sizeof(query),
        "UPDATE accounts SET sex = %d, skin = %d, money = %d, age = %d, passport = %d, quest = %d, quest_step = %d, quest_active = %d, last_login = %d WHERE id = %d",
        PlayerInfo[playerid][pSex], PlayerInfo[playerid][pSkin], PlayerInfo[playerid][pMoney],
        PlayerInfo[playerid][pAge], PlayerInfo[playerid][pPassport],
        PlayerInfo[playerid][pQuest], PlayerInfo[playerid][pQuestStep], PlayerInfo[playerid][pQuestActive],
        gettime(), PlayerInfo[playerid][pID]);
    db_free_result(db_query(Database, query));
    return 1;
}

stock ResetPlayerData(playerid)
{
    PlayerInfo[playerid][pID] = 0;
    PlayerInfo[playerid][pHash][0] = '\0';
    PlayerInfo[playerid][pSalt][0] = '\0';
    PlayerInfo[playerid][pSex] = 1;
    PlayerInfo[playerid][pSkin] = MaleSkins[0];
    PlayerInfo[playerid][pMoney] = 0;
    PlayerInfo[playerid][pAge] = 0;
    PlayerInfo[playerid][pPassport] = 0;
    PlayerInfo[playerid][pQuest] = Q_BOTTLES;
    PlayerInfo[playerid][pQuestStep] = 0;
    PlayerInfo[playerid][pQuestActive] = 0;
    PlayerInfo[playerid][pLogged] = false;
    PlayerInfo[playerid][pAccountExists] = false;
    PlayerInfo[playerid][pAuthShown] = false;
    PlayerInfo[playerid][pRegistering] = false;
    PlayerInfo[playerid][pLoginTries] = 0;
    PlayerInfo[playerid][pSkinIndex] = 0;
    PlayerInfo[playerid][pTempPass][0] = '\0';
    PlayerInfo[playerid][pRentVeh] = INVALID_VEHICLE_ID;
    PlayerInfo[playerid][pRentLeft] = 0;
    PlayerInfo[playerid][pQuestTimer] = 0;
    PlayerInfo[playerid][pQuestExtra] = 0;
    PlayerInfo[playerid][pCheckpoint] = PT_NONE;
    PlayerInfo[playerid][pAction] = ACT_NONE;
    PlayerInfo[playerid][pActionTime] = 0;
    PlayerInfo[playerid][pActionArg] = 0;
    for(new i = 0; i < MAX_BOTTLES; i++)
    {
        BottlePickup[playerid][i] = STREAMER_TAG_PICKUP:0;
        BottleIcon[playerid][i] = STREAMER_TAG_MAP_ICON:0;
    }
}

// =============================================================================
// 7. РЕЄСТРАЦІЯ ТА ВХІД
// =============================================================================
stock ShowLoginDialog(playerid, const error[] = "")
{
    new text[400];
    format(text, sizeof(text),
        "{FFFFFF}Вітаємо з поверненням на {FFFF00}%s{FFFFFF}, %s!\n\nАкаунт знайдено. Введіть пароль, щоб увійти.\n{AAAAAA}Спроб залишилось: %d\n\n{FF3333}%s",
        SERVER_NAME, GetName(playerid), MAX_LOGIN_TRIES - PlayerInfo[playerid][pLoginTries], error);
    ShowPlayerDialog(playerid, DIALOG_LOGIN, DIALOG_STYLE_PASSWORD, "{00FF00}Вхід в акаунт", text, "Увійти", "Вихід");
}

stock ShowRegisterDialog(playerid, const error[] = "")
{
    new text[400];
    format(text, sizeof(text),
        "{FFFFFF}Ласкаво просимо на {FFFF00}%s{FFFFFF}, %s!\n\nЦей нікнейм вільний. Придумайте пароль\n(від %d до %d символів), щоб створити акаунт.\n\n{FF3333}%s",
        SERVER_NAME, GetName(playerid), PASS_MIN_LEN, PASS_MAX_LEN, error);
    ShowPlayerDialog(playerid, DIALOG_REGISTER, DIALOG_STYLE_PASSWORD, "{00FF00}Реєстрація [1/4]", text, "Далі", "Вихід");
}

forward Auth_Show(playerid);
public Auth_Show(playerid)
{
    if(!IsPlayerConnected(playerid) || PlayerInfo[playerid][pLogged]) return 0;

    SetPlayerCameraPos(playerid, 1840.0, -1950.0, 45.0);
    SetPlayerCameraLookAt(playerid, SPAWN_X, SPAWN_Y, SPAWN_Z);

    if(PlayerInfo[playerid][pAccountExists]) ShowLoginDialog(playerid);
    else ShowRegisterDialog(playerid);
    return 1;
}

stock GetRegSkin(playerid, index)
{
    return (PlayerInfo[playerid][pSex] == 1) ? MaleSkins[index] : FemaleSkins[index];
}

stock ShowSkinDialog(playerid)
{
    new idx = PlayerInfo[playerid][pSkinIndex];
    new caption[64], text[300];
    if(PlayerInfo[playerid][pSex] == 1)
        format(caption, sizeof(caption), "{00FF00}Скін %d/3: %s", idx + 1, MaleSkinNames[idx]);
    else
        format(caption, sizeof(caption), "{00FF00}Скін %d/3: %s", idx + 1, FemaleSkinNames[idx]);

    format(text, sizeof(text), "Наступний скін >>\n<< Попередній скін\n{00FF00}Обрати цей образ");
    ShowPlayerDialog(playerid, DIALOG_SKIN, DIALOG_STYLE_LIST, caption, text, "Обрати", "");
}

stock SkinPreview_Start(playerid)
{
    SetPlayerInterior(playerid, 0);
    SetPlayerVirtualWorld(playerid, 1000 + playerid); // щоб ніхто не заважав
    SetPlayerPos(playerid, SPAWN_X, SPAWN_Y, SPAWN_Z);
    SetPlayerFacingAngle(playerid, SPAWN_A);
    SetPlayerSkin(playerid, GetRegSkin(playerid, PlayerInfo[playerid][pSkinIndex]));
    TogglePlayerControllable(playerid, false);

    // Камера стоїть перед персонажем (персонаж дивиться на схід)
    SetPlayerCameraPos(playerid, SPAWN_X + 3.5, SPAWN_Y, SPAWN_Z + 0.8);
    SetPlayerCameraLookAt(playerid, SPAWN_X, SPAWN_Y, SPAWN_Z + 0.3);
    ShowSkinDialog(playerid);
}

stock Registration_Finish(playerid)
{
    PlayerInfo[playerid][pSkin] = GetRegSkin(playerid, PlayerInfo[playerid][pSkinIndex]);
    PlayerInfo[playerid][pMoney] = START_MONEY;
    Account_Create(playerid);

    PlayerInfo[playerid][pRegistering] = false;
    PlayerInfo[playerid][pLogged] = true;

    SetSpawnInfo(playerid, 0, PlayerInfo[playerid][pSkin], SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_A, 0, 0, 0, 0, 0, 0);
    SetPlayerVirtualWorld(playerid, 0);
    SetPlayerSkin(playerid, PlayerInfo[playerid][pSkin]);
    TogglePlayerControllable(playerid, true);
    SetCameraBehindPlayer(playerid);
    GiveMoney(playerid, 0);

    new msg[144];
    format(msg, sizeof(msg), "Акаунт створено! Ваш номер: %d. Стартовий капітал: %d грн.", PlayerInfo[playerid][pID], START_MONEY);
    SendClientMessage(playerid, COLOR_GREEN, msg);
    SendClientMessage(playerid, COLOR_YELLOW, "Підійдіть до бомжа Віталіка (червоний маркер) — він підкаже, з чого почати.");
    SendClientMessage(playerid, COLOR_GREY, "Дія біля NPC: ALT або /d. Усі команди: /help. Меню: /menu");
    PreloadAnimations(playerid);
    Quest_Resume(playerid);
}

// =============================================================================
// 8. ОРЕНДА МОПЕДІВ
// =============================================================================
stock ShowRentDialog(playerid)
{
    if(PlayerInfo[playerid][pRentVeh] != INVALID_VEHICLE_ID)
    {
        new msg[144];
        format(msg, sizeof(msg), "У вас вже є орендований мопед (залишилось %d хв). Повернути: /unrent", PlayerInfo[playerid][pRentLeft] / 60 + 1);
        SendClientMessage(playerid, COLOR_GREY, msg);
        return 0;
    }

    new text[256], line[64];
    for(new i = 0; i < sizeof(RentTariffs); i++)
    {
        format(line, sizeof(line), "%d хвилин — {00FF00}%d грн\n", RentTariffs[i][trMinutes], RentTariffs[i][trPrice]);
        strcat(text, line);
    }
    ShowPlayerDialog(playerid, DIALOG_RENT, DIALOG_STYLE_LIST, "{FFFF00}Прокат мопедів «Бджілка»", text, "Орендувати", "Скасувати");
    return 1;
}

stock Rent_Start(playerid, tariff)
{
    new price = RentTariffs[tariff][trPrice];
    if(PlayerInfo[playerid][pMoney] < price)
    {
        SendClientMessage(playerid, COLOR_RED, "Недостатньо грошей для оренди. Віталік радить пошукати пляшки :)");
        return 0;
    }

    GiveMoney(playerid, -price);

    new Float:offset = float(playerid % 5) * 1.5;
    new veh = CreateVehicle(RENT_MODEL, RENT_SPAWN_X, RENT_SPAWN_Y - offset, RENT_SPAWN_Z, RENT_SPAWN_A, random(127), random(127), -1);
    if(veh == INVALID_VEHICLE_ID)
    {
        GiveMoney(playerid, price);
        SendClientMessage(playerid, COLOR_RED, "Вільних мопедів немає, спробуйте пізніше.");
        return 0;
    }
    SetVehicleNumberPlate(veh, "BEREZA");
    SetVehicleToRespawn(veh); // щоб номер застосувався
    RentOwner[veh] = playerid;
    PlayerInfo[playerid][pRentVeh] = veh;
    PlayerInfo[playerid][pRentLeft] = RentTariffs[tariff][trMinutes] * 60;
    PutPlayerInVehicle(playerid, veh, 0);

    new msg[144];
    format(msg, sizeof(msg), "Ви орендували мопед на %d хв за %d грн. Повернути раніше: /unrent", RentTariffs[tariff][trMinutes], price);
    SendClientMessage(playerid, COLOR_GREEN, msg);

    if(PlayerInfo[playerid][pQuestActive] && PlayerInfo[playerid][pQuest] == Q_MOPED)
    {
        SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «Оце я розумію — байкер! Тримай компенсацію за оренду.»");
        Quest_Complete(playerid, 150);
    }
    return 1;
}

stock Rent_End(playerid, const reason[] = "")
{
    new veh = PlayerInfo[playerid][pRentVeh];
    if(veh == INVALID_VEHICLE_ID) return 0;

    RentOwner[veh] = INVALID_PLAYER_ID;
    DestroyVehicle(veh);
    PlayerInfo[playerid][pRentVeh] = INVALID_VEHICLE_ID;
    PlayerInfo[playerid][pRentLeft] = 0;
    if(reason[0] != '\0') SendClientMessage(playerid, COLOR_ORANGE, reason);
    return 1;
}

stock Rent_Tick(playerid)
{
    if(PlayerInfo[playerid][pRentVeh] == INVALID_VEHICLE_ID) return 0;

    PlayerInfo[playerid][pRentLeft]--;
    if(PlayerInfo[playerid][pRentLeft] == 60)
    {
        SendClientMessage(playerid, COLOR_ORANGE, "[Оренда] Залишилась 1 хвилина оренди мопеда!");
    }
    else if(PlayerInfo[playerid][pRentLeft] <= 0)
    {
        Rent_End(playerid, "[Оренда] Час оренди вийшов, мопед повернуто в прокат.");
    }
    return 1;
}

// =============================================================================
// 9. ДВИГУН КВЕСТІВ
// =============================================================================
stock Quest_SetCP(playerid, pt)
{
    DisablePlayerCheckpoint(playerid);
    SetPlayerCheckpoint(playerid, Points[pt][ptX], Points[pt][ptY], Points[pt][ptZ], Points[pt][ptSize]);
    PlayerInfo[playerid][pCheckpoint] = pt;
}

stock Quest_ClearMarkers(playerid)
{
    DisablePlayerCheckpoint(playerid);
    PlayerInfo[playerid][pCheckpoint] = PT_NONE;
    for(new i = 0; i < MAX_BOTTLES; i++)
    {
        if(BottlePickup[playerid][i] != STREAMER_TAG_PICKUP:0)
        {
            DestroyDynamicPickup(BottlePickup[playerid][i]);
            BottlePickup[playerid][i] = STREAMER_TAG_PICKUP:0;
        }
        if(BottleIcon[playerid][i] != STREAMER_TAG_MAP_ICON:0)
        {
            DestroyDynamicMapIcon(BottleIcon[playerid][i]);
            BottleIcon[playerid][i] = STREAMER_TAG_MAP_ICON:0;
        }
    }
}

stock GetNextDumpster(playerid)
{
    for(new i = 0; i < MAX_DUMPSTERS; i++)
    {
        if(!(PlayerInfo[playerid][pQuestStep] & (1 << i))) return i;
    }
    return -1;
}

stock IsInMayor(playerid)
{
    return (GetPlayerVirtualWorld(playerid) == MAYOR_VW && GetPlayerInterior(playerid) == MAYOR_INT_ID);
}

// Ставить маркери (чекпоінт, пікапи) відповідно до поточного кроку квесту
stock Quest_UpdateMarkers(playerid)
{
    Quest_ClearMarkers(playerid);
    new q = PlayerInfo[playerid][pQuest];
    if(q >= MAX_QUESTS) return 0;

    if(!PlayerInfo[playerid][pQuestActive])
    {
        if(q == Q_BOTTLES) Quest_SetCP(playerid, PT_VITALIK);
        return 1;
    }

    new step = PlayerInfo[playerid][pQuestStep];
    switch(q)
    {
        case Q_BOTTLES:
        {
            for(new i = 0; i < MAX_BOTTLES; i++)
            {
                if(step & (1 << i)) continue;
                BottlePickup[playerid][i] = CreateDynamicPickup(1486, 1, BottlePos[i][0], BottlePos[i][1], BottlePos[i][2], 0, 0, playerid);
                BottleIcon[playerid][i] = CreateDynamicMapIcon(BottlePos[i][0], BottlePos[i][1], BottlePos[i][2], 0, 0x33FF33FF, 0, 0, playerid);
            }
        }
        case Q_RECYCLE:  Quest_SetCP(playerid, PT_RECYCLE);
        case Q_MOPED:    Quest_SetCP(playerid, PT_RENT);
        case Q_SHAWARMA: Quest_SetCP(playerid, (step == 0) ? PT_SHAWARMA : PT_GROVE);
        case Q_MUSIC:    Quest_SetCP(playerid, PT_STAGE);
        case Q_FISH:     Quest_SetCP(playerid, PT_PIER);
        case Q_SWIM:     Quest_SetCP(playerid, PT_WATER);
        case Q_DUMPSTER:
        {
            new d = GetNextDumpster(playerid);
            if(d != -1)
            {
                SetPlayerCheckpoint(playerid, DumpsterPos[d][0], DumpsterPos[d][1], DumpsterPos[d][2], 1.5);
                PlayerInfo[playerid][pCheckpoint] = PT_DUMPSTER;
            }
        }
        case Q_LOTTERY:  Quest_SetCP(playerid, PT_LOTTERY);
        case Q_MAYOR:    Quest_SetCP(playerid, IsInMayor(playerid) ? PT_MAYOR_DESK : PT_MAYOR_DOOR);
        case Q_PHOTO:
        {
            if(step == 0) Quest_SetCP(playerid, PT_PHOTO);
            else Quest_SetCP(playerid, IsInMayor(playerid) ? PT_MAYOR_DESK : PT_MAYOR_DOOR);
        }
        case Q_INTERVIEW: Quest_SetCP(playerid, PT_BURGER);
        case Q_SHIFT:    Quest_SetCP(playerid, PT_DELIVERY_1 + step);
        case Q_FINAL:    Quest_SetCP(playerid, PT_VITALIK);
    }
    return 1;
}

// Викликається при прийнятті квесту і при кожному спавні (після входу/смерті)
stock Quest_Resume(playerid)
{
    Action_Stop(playerid);
    new q = PlayerInfo[playerid][pQuest];

    if(q >= MAX_QUESTS)
    {
        Quest_ClearMarkers(playerid);
        return 0;
    }
    if(!PlayerInfo[playerid][pQuestActive])
    {
        Quest_UpdateMarkers(playerid);
        if(q != Q_BOTTLES) SendClientMessage(playerid, COLOR_SMS, "[SMS] У вас є непрочитане завдання від Віталіка. Відкрити: /quest");
        return 1;
    }

    // Скидаємо "гарячі" стани, які не мають сенсу після перезаходу/смерті
    switch(q)
    {
        case Q_SHAWARMA:
        {
            if(PlayerInfo[playerid][pQuestStep] == 1) SendClientMessage(playerid, COLOR_GREY, "Шаурма впала й зіпсувалась. Ашот зробить нову — повертайтесь до нього.");
            PlayerInfo[playerid][pQuestStep] = 0;
            PlayerInfo[playerid][pQuestTimer] = 0;
        }
        case Q_INTERVIEW:
        {
            PlayerInfo[playerid][pQuestStep] = 0;
            PlayerInfo[playerid][pQuestExtra] = 0;
        }
        case Q_SHIFT: PlayerInfo[playerid][pQuestTimer] = SHIFT_TIME;
        case Q_MUSIC: PlayerInfo[playerid][pQuestExtra] = 0;
    }

    Quest_UpdateMarkers(playerid);

    new msg[144];
    format(msg, sizeof(msg), "[Квест %d/%d] %s", q + 1, MAX_QUESTS, QuestName[q]);
    SendClientMessage(playerid, COLOR_YELLOW, msg);
    format(msg, sizeof(msg), "Завдання: %s", QuestGoal[q]);
    SendClientMessage(playerid, COLOR_WHITE, msg);

    if(q == Q_QUIZ) Quiz_Show(playerid);
    else if(q == Q_MOPED && PlayerInfo[playerid][pRentVeh] != INVALID_VEHICLE_ID)
    {
        SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «О, то в тебе вже є мопед! Молодець, тримай компенсацію.»");
        Quest_Complete(playerid, 150);
    }
    return 1;
}

stock Quest_Offer(playerid)
{
    new q = PlayerInfo[playerid][pQuest];
    if(q >= MAX_QUESTS) return 0;

    new caption[96], text[1024];
    format(caption, sizeof(caption), "{FFFF00}Квест %d/%d: %s", q + 1, MAX_QUESTS, QuestName[q]);
    format(text, sizeof(text), "{FFFFFF}%s\n\n{FFFF00}Завдання: {FFFFFF}%s", QuestStory[q], QuestGoal[q]);
    ShowPlayerDialog(playerid, DIALOG_QUEST_OFFER, DIALOG_STYLE_MSGBOX, caption, text, "Прийняти", "Пізніше");
    return 1;
}

stock Quest_Accept(playerid)
{
    PlayerInfo[playerid][pQuestActive] = 1;
    PlayerInfo[playerid][pQuestStep] = 0;
    PlayerInfo[playerid][pQuestExtra] = 0;
    PlayerInfo[playerid][pQuestTimer] = 0;
    Account_Save(playerid);
    Quest_Resume(playerid);
}

stock Quest_Complete(playerid, reward)
{
    new q = PlayerInfo[playerid][pQuest];

    Action_Stop(playerid);
    Quest_ClearMarkers(playerid);
    PlayerInfo[playerid][pQuestTimer] = 0;
    PlayerInfo[playerid][pQuestExtra] = 0;
    PlayerInfo[playerid][pQuestStep] = 0;
    PlayerInfo[playerid][pQuestActive] = 0;
    PlayerInfo[playerid][pQuest] = q + 1;

    if(reward > 0) GiveMoney(playerid, reward);

    new msg[144];
    format(msg, sizeof(msg), "[Квест виконано] «%s» — нагорода: %d грн. Прогрес: %d/%d", QuestName[q], reward, q + 1, MAX_QUESTS);
    SendClientMessage(playerid, COLOR_GREEN, msg);
    format(msg, sizeof(msg), "~y~QUEST COMPLETE~n~~g~+%d", reward);
    GameTextForPlayer(playerid, msg, 3000, 1);
    PlayerPlaySound(playerid, 1057, 0.0, 0.0, 0.0);

    Account_Save(playerid);

    if(q + 1 < MAX_QUESTS)
    {
        SetTimerEx("Quest_SendNext", 4000, false, "ii", playerid, q + 1);
    }
}

forward Quest_SendNext(playerid, quest);
public Quest_SendNext(playerid, quest)
{
    if(!IsPlayerConnected(playerid) || !PlayerInfo[playerid][pLogged]) return 0;
    if(PlayerInfo[playerid][pQuest] != quest || PlayerInfo[playerid][pQuestActive]) return 0;

    // Ці два квести видають не SMS, а люди на місці
    if(quest != Q_PHOTO && quest != Q_SHIFT)
    {
        SendClientMessage(playerid, COLOR_SMS, "[SMS] Віталік надіслав вам нове повідомлення.");
        PlayerPlaySound(playerid, 1058, 0.0, 0.0, 0.0);
    }
    Quest_Offer(playerid);
    return 1;
}

stock Quest_ShowCurrent(playerid)
{
    new q = PlayerInfo[playerid][pQuest];
    if(q >= MAX_QUESTS)
    {
        ShowInfo(playerid, "{FFFF00}Квести", "{FFFFFF}Ви виконали всі 15 початкових квестів!\nВіталік пишається вами. Тепер місто — ваше.");
        return 1;
    }
    if(!PlayerInfo[playerid][pQuestActive])
    {
        if(q == Q_BOTTLES)
        {
            SendClientMessage(playerid, COLOR_YELLOW, "Поговоріть з бомжем Віталіком біля вокзалу (червоний маркер, ALT або /d).");
            Quest_SetCP(playerid, PT_VITALIK);
        }
        else Quest_Offer(playerid);
        return 1;
    }
    if(q == Q_QUIZ)
    {
        Quiz_Show(playerid);
        return 1;
    }

    new caption[96], text[512];
    format(caption, sizeof(caption), "{FFFF00}Квест %d/%d: %s", q + 1, MAX_QUESTS, QuestName[q]);
    format(text, sizeof(text), "{FFFFFF}Поточне завдання:\n{FFFF00}%s", QuestGoal[q]);
    switch(q)
    {
        case Q_BOTTLES:
        {
            new line[64];
            format(line, sizeof(line), "\n\n{FFFFFF}Зібрано пляшок: %d/%d", CountBits(PlayerInfo[playerid][pQuestStep]), MAX_BOTTLES);
            strcat(text, line);
        }
        case Q_FISH:
        {
            new line[64];
            format(line, sizeof(line), "\n\n{FFFFFF}Спіймано риби: %d/3", PlayerInfo[playerid][pQuestStep]);
            strcat(text, line);
        }
        case Q_DUMPSTER:
        {
            new line[64];
            format(line, sizeof(line), "\n\n{FFFFFF}Обшукано смітників: %d/%d", CountBits(PlayerInfo[playerid][pQuestStep]), MAX_DUMPSTERS);
            strcat(text, line);
        }
        case Q_SHIFT:
        {
            new line[128];
            format(line, sizeof(line), "\n\n{FFFFFF}Замовлення %d/3: %s", PlayerInfo[playerid][pQuestStep] + 1, DeliveryNames[PlayerInfo[playerid][pQuestStep]]);
            strcat(text, line);
        }
    }
    ShowInfo(playerid, caption, text);
    Quest_UpdateMarkers(playerid);
    return 1;
}

// --- Квест 3: іспит ---
stock Quiz_Show(playerid)
{
    new idx = PlayerInfo[playerid][pQuestStep];
    if(idx >= sizeof(QuizData)) idx = 0;
    new caption[96], answers[300];
    format(caption, sizeof(caption), "{FFFF00}%d/%d: %s", idx + 1, sizeof(QuizData), QuizData[idx][qzQuestion]);
    format(answers, sizeof(answers), "%s", QuizData[idx][qzAnswers]);
    ShowPlayerDialog(playerid, DIALOG_Q_QUIZ, DIALOG_STYLE_LIST, caption, answers, "Відповісти", "Пізніше");
}

// --- Квест 13: співбесіда ---
stock Interview_Show(playerid)
{
    new idx = PlayerInfo[playerid][pQuestStep];
    new caption[96], answers[300];
    format(caption, sizeof(caption), "{FFFF00}Менеджер: %s", InterviewData[idx][ivQuestion]);
    format(answers, sizeof(answers), "%s", InterviewData[idx][ivAnswers]);
    ShowPlayerDialog(playerid, DIALOG_Q_INTERVIEW, DIALOG_STYLE_LIST, caption, answers, "Відповісти", "Втекти");
}

// --- Квест 15: фінал ---
stock Final_Show(playerid)
{
    new text[1500];
    strcat(text, "{FFFFFF}Віталік: «Сідай. Колись у мене було все: мережа шаурмичних по всьому\nСан-Андреасу, яхта і золотий мопед. Але гроші зробили мене порожнім.\n");
    strcat(text, "Тож я пішов жити на вокзал — шукати справжніх людей.\n\n");
    strcat(text, "Кожне завдання було перевіркою. Пляшки — чи не лінивий. Борис — чи не жадібний.\nІспит — чи знаєш закон. Шаурма — чи надійний.\n");
    strcat(text, "А квиток у смітнику... то я його туди поклав.\n\n");
    strcat(text, "Ти пройшов. Тримай {00FF00}5000 грн{FFFFFF} на старт. І скажи мені: ким ти хочеш бути далі?»");
    ShowPlayerDialog(playerid, DIALOG_Q_FINAL, DIALOG_STYLE_MSGBOX, "{FFFF00}Секрет Віталіка", text, "Далі", "");
}

stock FinalSkin_Show(playerid)
{
    ShowPlayerDialog(playerid, DIALOG_Q_FINAL_SKIN, DIALOG_STYLE_LIST, "{FFFF00}Ким ти будеш далі?",
        "Залишити свій образ (справжній бомж не здається!)\nПовсякденний одяг\nРобочий одяг\nДіловий стиль",
        "Обрати", "");
}

stock Vitalik_Talk(playerid)
{
    new q = PlayerInfo[playerid][pQuest];
    if(q == Q_BOTTLES && !PlayerInfo[playerid][pQuestActive])
    {
        Quest_Offer(playerid);
        return 1;
    }
    if(q == Q_FINAL && PlayerInfo[playerid][pQuestActive])
    {
        Final_Show(playerid);
        return 1;
    }
    SendClientMessage(playerid, COLOR_WHITE, VitalikPhrases[random(sizeof(VitalikPhrases))]);
    if(q < MAX_QUESTS) SendClientMessage(playerid, COLOR_GREY, "Поточне завдання: /quest");
    return 1;
}

// =============================================================================
// 10. ДІЇ (концерт, риболовля, фото, обшук смітників)
// =============================================================================
stock Action_Start(playerid, action, arg = 0)
{
    if(PlayerInfo[playerid][pAction] != ACT_NONE)
    {
        SendClientMessage(playerid, COLOR_GREY, "Ви вже чимось зайняті.");
        return 0;
    }
    if(GetPlayerState(playerid) != PLAYER_STATE_ONFOOT)
    {
        SendClientMessage(playerid, COLOR_GREY, "Спочатку вийдіть з транспорту.");
        return 0;
    }
    PlayerInfo[playerid][pAction] = action;
    PlayerInfo[playerid][pActionTime] = 0;
    PlayerInfo[playerid][pActionArg] = arg;
    GetPlayerPos(playerid, PlayerInfo[playerid][pActX], PlayerInfo[playerid][pActY], PlayerInfo[playerid][pActZ]);

    switch(action)
    {
        case ACT_SING:
        {
            ApplyAnimation(playerid, "DANCING", "DAN_Loop_A", 4.1, 1, 0, 0, 0, 0, 1);
            SendLocalMessage(playerid, 20.0, COLOR_PURPLE, "* Хтось почав співати на всю площу. Голуби злякано злетіли.");
            SendClientMessage(playerid, COLOR_YELLOW, "Концерт почався! Не йдіть зі сцени 30 секунд.");
        }
        case ACT_FISH:
        {
            ApplyAnimation(playerid, "SAMP", "FishingIdle", 4.1, 1, 0, 0, 1, 0, 1);
            SendClientMessage(playerid, COLOR_YELLOW, "Ви закинули вудку... Чекаємо, поки клюне.");
        }
        case ACT_PHOTO:
        {
            SendClientMessage(playerid, COLOR_YELLOW, "Фотограф: «Дивимось у камеру і НЕ РУХАЄМОСЬ 10 секунд! Пташка вилітає...»");
        }
        case ACT_SEARCH:
        {
            ApplyAnimation(playerid, "BOMBER", "BOM_Plant_Loop", 4.1, 1, 0, 0, 1, 0, 1);
            SendClientMessage(playerid, COLOR_YELLOW, "Ви риєтесь у смітнику...");
        }
    }
    return 1;
}

stock Action_Stop(playerid)
{
    if(PlayerInfo[playerid][pAction] == ACT_NONE) return 0;
    PlayerInfo[playerid][pAction] = ACT_NONE;
    PlayerInfo[playerid][pActionTime] = 0;
    ClearAnimations(playerid, 1);
    return 1;
}

stock Action_Tick(playerid)
{
    if(PlayerInfo[playerid][pAction] == ACT_NONE) return 0;

    new t = ++PlayerInfo[playerid][pActionTime];
    new msg[144];

    switch(PlayerInfo[playerid][pAction])
    {
        case ACT_SING:
        {
            if(!IsNearPoint(playerid, PT_STAGE, 5.0))
            {
                Action_Stop(playerid);
                SendClientMessage(playerid, COLOR_RED, "Ви пішли зі сцени — публіка розійшлась. Почніть заново: /sing");
                return 1;
            }
            if(t % 5 == 0)
            {
                new coins = 5 + random(36);
                PlayerInfo[playerid][pQuestExtra] += coins;
                GiveMoney(playerid, coins);
                switch(random(4))
                {
                    case 0: format(msg, sizeof(msg), "Перехожий кинув вам %d грн.", coins);
                    case 1: format(msg, sizeof(msg), "Бабуся дала %d грн і сказала, що ви схожі на її онука.", coins);
                    case 2: format(msg, sizeof(msg), "Турист сфотографувався з вами і заплатив %d грн.", coins);
                    default: format(msg, sizeof(msg), "Поліцейський зітхнув, але все ж кинув %d грн.", coins);
                }
                SendClientMessage(playerid, COLOR_GREEN, msg);
            }
            if(t >= SING_TIME)
            {
                format(msg, sizeof(msg), "Оплески! За концерт ви зібрали %d грн. Віталік додає бонус.", PlayerInfo[playerid][pQuestExtra]);
                SendClientMessage(playerid, COLOR_YELLOW, msg);
                Quest_Complete(playerid, 100);
            }
        }
        case ACT_FISH:
        {
            if(!IsNearPoint(playerid, PT_PIER, 8.0))
            {
                Action_Stop(playerid);
                SendClientMessage(playerid, COLOR_RED, "Ви пішли з пірсу, риба попливла.");
                return 1;
            }
            if(t >= FISH_TIME)
            {
                Action_Stop(playerid);
                new roll = random(100);
                if(roll < 55)
                {
                    new const fishNames[4][] = {"коропа", "окуня", "щуку", "морського карася"};
                    PlayerInfo[playerid][pQuestStep]++;
                    format(msg, sizeof(msg), "Клює! Ви спіймали %s. Риби: %d/3", fishNames[random(4)], PlayerInfo[playerid][pQuestStep]);
                    SendClientMessage(playerid, COLOR_GREEN, msg);
                    if(PlayerInfo[playerid][pQuestStep] >= 3)
                    {
                        SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «Риба є, вечеря буде! Рибу забираю, а тобі — гроші.»");
                        Quest_Complete(playerid, 250);
                        return 1;
                    }
                }
                else if(roll < 75) SendClientMessage(playerid, COLOR_GREY, "Ви витягли старий чобіт. Майже риба...");
                else if(roll < 90) SendClientMessage(playerid, COLOR_GREY, "Риба сьогодні розумна — гачок порожній.");
                else
                {
                    GiveMoney(playerid, 150);
                    SendClientMessage(playerid, COLOR_GREEN, "Ви витягли чийсь гаманець! Всередині 150 грн.");
                }
                SendClientMessage(playerid, COLOR_GREY, "Закинути ще раз: ALT або /fish");
            }
        }
        case ACT_PHOTO:
        {
            if(t == 1)
            {
                // перша секунда — даємо гравцю зупинитися після входу в маркер
                GetPlayerPos(playerid, PlayerInfo[playerid][pActX], PlayerInfo[playerid][pActY], PlayerInfo[playerid][pActZ]);
                return 1;
            }
            if(!IsPlayerInRangeOfPoint(playerid, 0.4, PlayerInfo[playerid][pActX], PlayerInfo[playerid][pActY], PlayerInfo[playerid][pActZ]))
            {
                PlayerInfo[playerid][pActionTime] = 0;
                if(!IsNearPoint(playerid, PT_PHOTO, 6.0))
                {
                    Action_Stop(playerid);
                    SendClientMessage(playerid, COLOR_RED, "Ви втекли з фотоательє. Поверніться в маркер, щоб спробувати знову.");
                    Quest_UpdateMarkers(playerid);
                    return 1;
                }
                GetPlayerPos(playerid, PlayerInfo[playerid][pActX], PlayerInfo[playerid][pActY], PlayerInfo[playerid][pActZ]);
                SendClientMessage(playerid, COLOR_RED, "Фотограф: «Ви поворухнулись! Ще раз, і не кліпаємо!»");
                return 1;
            }
            format(msg, sizeof(msg), "~w~%d", PHOTO_TIME + 1 - t);
            GameTextForPlayer(playerid, msg, 1100, 3);
            if(t > PHOTO_TIME)
            {
                Action_Stop(playerid);
                PlayerInfo[playerid][pQuestStep] = 1;
                SendClientMessage(playerid, COLOR_YELLOW, "Фотограф: «Чудово! Ви вийшли... ну, як вийшли.» Тепер несіть фото в мерію.");
                Account_Save(playerid);
                Quest_UpdateMarkers(playerid);
            }
        }
        case ACT_SEARCH:
        {
            if(t >= SEARCH_TIME)
            {
                new d = PlayerInfo[playerid][pActionArg];
                Action_Stop(playerid);
                PlayerInfo[playerid][pQuestStep] |= (1 << d);
                new found = CountBits(PlayerInfo[playerid][pQuestStep]);
                if(found >= MAX_DUMPSTERS)
                {
                    SendClientMessage(playerid, COLOR_GREEN, "Ви знайшли... ЛОТЕРЕЙНИЙ КВИТОК! Віталік має про це знати.");
                    Quest_Complete(playerid, 50);
                    return 1;
                }
                new loot = random(sizeof(DumpsterLoot));
                format(msg, sizeof(msg), "Ви знайшли %s (%d/%d)", DumpsterLoot[loot], found, MAX_DUMPSTERS);
                SendClientMessage(playerid, COLOR_WHITE, msg);
                if(loot == 1) GiveMoney(playerid, 80);
                else if(loot == 4) GiveMoney(playerid, 45);
                else if(loot == 3)
                {
                    new Float:hp;
                    GetPlayerHealth(playerid, hp);
                    SetPlayerHealth(playerid, (hp > 6.0) ? (hp - 5.0) : 1.0);
                }
                Account_Save(playerid);
                Quest_UpdateMarkers(playerid);
            }
        }
    }
    return 1;
}

stock QuestTimer_Tick(playerid)
{
    if(PlayerInfo[playerid][pQuestTimer] <= 0) return 0;

    PlayerInfo[playerid][pQuestTimer]--;
    new left = PlayerInfo[playerid][pQuestTimer];
    new str[32];
    format(str, sizeof(str), "~y~%d:%02d", left / 60, left % 60);
    GameTextForPlayer(playerid, str, 1100, 3);

    if(left > 0) return 1;

    switch(PlayerInfo[playerid][pQuest])
    {
        case Q_SHAWARMA:
        {
            PlayerInfo[playerid][pQuestStep] = 0;
            SendClientMessage(playerid, COLOR_RED, "Шаурма охолола! Клієнт відмовився. Повертайтесь до Ашота за новою.");
            Quest_UpdateMarkers(playerid);
        }
        case Q_SHIFT:
        {
            PlayerInfo[playerid][pQuestTimer] = -1;
            SendClientMessage(playerid, COLOR_RED, "Ви запізнились! Клієнт буркнув, що чайових не буде. Але замовлення все одно доставте.");
        }
    }
    return 1;
}

// Універсальна дія: ALT або /d
stock HandleAction(playerid)
{
    if(!PlayerInfo[playerid][pLogged]) return 0;
    if(GetPlayerState(playerid) != PLAYER_STATE_ONFOOT) return 0;

    // Вхід у мерію
    if(GetPlayerVirtualWorld(playerid) == 0 && IsPlayerInRangeOfPoint(playerid, 2.0, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z))
    {
        SetPlayerInterior(playerid, MAYOR_INT_ID);
        SetPlayerVirtualWorld(playerid, MAYOR_VW);
        SetPlayerPos(playerid, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z);
        SetPlayerFacingAngle(playerid, MAYOR_INT_A);
        SetCameraBehindPlayer(playerid);
        Quest_UpdateMarkers(playerid);
        return 1;
    }
    // Вихід з мерії
    if(IsInMayor(playerid) && IsPlayerInRangeOfPoint(playerid, 2.0, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z))
    {
        SetPlayerInterior(playerid, 0);
        SetPlayerVirtualWorld(playerid, 0);
        SetPlayerPos(playerid, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z);
        SetPlayerFacingAngle(playerid, MAYOR_ENT_A);
        SetCameraBehindPlayer(playerid);
        Quest_UpdateMarkers(playerid);
        return 1;
    }
    if(GetPlayerVirtualWorld(playerid) != 0) return 0;

    if(IsNearPoint(playerid, PT_VITALIK, 2.5)) return Vitalik_Talk(playerid);
    if(IsNearPoint(playerid, PT_RENT, 2.5))
    {
        ShowRentDialog(playerid);
        return 1;
    }

    new q = PlayerInfo[playerid][pQuest];
    new bool:active = (PlayerInfo[playerid][pQuestActive] != 0);

    if(IsNearPoint(playerid, PT_STAGE, 5.0))
    {
        if(active && q == Q_MUSIC)
        {
            Action_Start(playerid, ACT_SING);
            return 1;
        }
        SendClientMessage(playerid, COLOR_GREY, "Сцена вільна, але співати зараз немає настрою.");
        return 1;
    }
    if(IsNearPoint(playerid, PT_PIER, 8.0))
    {
        if(active && q == Q_FISH)
        {
            Action_Start(playerid, ACT_FISH);
            return 1;
        }
        SendClientMessage(playerid, COLOR_GREY, "Гарний вид на океан. Вудки у вас поки немає.");
        return 1;
    }
    for(new d = 0; d < MAX_DUMPSTERS; d++)
    {
        if(!IsPlayerInRangeOfPoint(playerid, 2.5, DumpsterPos[d][0], DumpsterPos[d][1], DumpsterPos[d][2])) continue;
        if(active && q == Q_DUMPSTER)
        {
            if(PlayerInfo[playerid][pQuestStep] & (1 << d))
            {
                SendClientMessage(playerid, COLOR_GREY, "Цей смітник ви вже обшукали. Тут лишились тільки щури.");
                return 1;
            }
            Action_Start(playerid, ACT_SEARCH, d);
            return 1;
        }
        SendClientMessage(playerid, COLOR_GREY, "Смітник порожній... Навіть щури звідси переїхали.");
        return 1;
    }
    return 0;
}

// =============================================================================
// 11. ІНІЦІАЛІЗАЦІЯ СЕРВЕРА
// =============================================================================
main()
{
    print("\n----------------------------------");
    print(" Bereza RolePlay v2.0 успішно запущено! ");
    print("----------------------------------\n");
}

public OnGameModeInit()
{
    DB_Init();

    SetGameModeText("Bereza RP v2.0");
    SendRconCommand("mapname San Andreas");
    DisableInteriorEnterExits();
    EnableStuntBonusForAll(0);
    UsePlayerPedAnims();

    for(new i = 0; i < MAX_VEHICLES; i++) RentOwner[i] = INVALID_PLAYER_ID;

    AddPlayerClass(MaleSkins[0], SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_A, 0, 0, 0, 0, 0, 0);

    // --- Віталік (скін 212 — бомж) ---
    new actor = CreateActor(212, Points[PT_VITALIK][ptX], Points[PT_VITALIK][ptY], Points[PT_VITALIK][ptZ], 94.7653);
    SetActorInvulnerable(actor);
    CreateDynamic3DTextLabel("{FFFF00}Бомж Віталік\n{FFFFFF}Король вокзалу\n{00FF00}ALT{FFFFFF} або {00FF00}/d{FFFFFF} — поговорити", COLOR_WHITE, Points[PT_VITALIK][ptX], Points[PT_VITALIK][ptY], Points[PT_VITALIK][ptZ] + 1.0, 10.0);

    // --- Прокат мопедів ---
    CreateDynamicPickup(1239, 23, Points[PT_RENT][ptX], Points[PT_RENT][ptY], Points[PT_RENT][ptZ]);
    CreateDynamic3DTextLabel("{FFFF00}Прокат мопедів «Бджілка»\n{FFFFFF}від {00FF00}99 грн{FFFFFF} / 15 хв\n{00FF00}ALT{FFFFFF} або {00FF00}/rent", COLOR_WHITE, Points[PT_RENT][ptX], Points[PT_RENT][ptY], Points[PT_RENT][ptZ] + 0.6, 10.0);

    // --- Квестові локації ---
    CreateDynamic3DTextLabel("{FFFF00}Пункт прийому склотари\n{FFFFFF}«Дзвінка пляшка»", COLOR_WHITE, Points[PT_RECYCLE][ptX], Points[PT_RECYCLE][ptY], Points[PT_RECYCLE][ptZ] + 0.6, 10.0);
    CreateDynamic3DTextLabel("{FFFF00}Шаурма в Ашота\n{FFFFFF}Найсмачніша в Айдлвуді", COLOR_WHITE, Points[PT_SHAWARMA][ptX], Points[PT_SHAWARMA][ptY], Points[PT_SHAWARMA][ptZ] + 0.6, 10.0);
    CreateDynamic3DTextLabel("{FFFF00}Вулична сцена\n{FFFFFF}Співай — і тобі накидають грошей", COLOR_WHITE, Points[PT_STAGE][ptX], Points[PT_STAGE][ptY], Points[PT_STAGE][ptZ] + 0.6, 15.0);
    CreateDynamic3DTextLabel("{FFFF00}Місце для риболовлі", COLOR_WHITE, Points[PT_PIER][ptX], Points[PT_PIER][ptY], Points[PT_PIER][ptZ] + 0.6, 15.0);
    CreateDynamic3DTextLabel("{FFFF00}Лотерейний кіоск", COLOR_WHITE, Points[PT_LOTTERY][ptX], Points[PT_LOTTERY][ptY], Points[PT_LOTTERY][ptZ] + 0.6, 10.0);
    CreateDynamic3DTextLabel("{FFFF00}Фотоательє «Пташка»\n{FFFFFF}Фото на документи", COLOR_WHITE, Points[PT_PHOTO][ptX], Points[PT_PHOTO][ptY], Points[PT_PHOTO][ptZ] + 0.6, 10.0);
    CreateDynamic3DTextLabel("{FFFF00}Burger Shot\n{FFFFFF}Відділ кадрів", COLOR_WHITE, Points[PT_BURGER][ptX], Points[PT_BURGER][ptY], Points[PT_BURGER][ptZ] + 0.6, 10.0);

    new ashot = CreateActor(155, Points[PT_SHAWARMA][ptX] + 1.5, Points[PT_SHAWARMA][ptY], Points[PT_SHAWARMA][ptZ], 90.0);
    SetActorInvulnerable(ashot);

    // --- Мерія ---
    CreateDynamicPickup(1318, 23, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z);
    CreateDynamic3DTextLabel("{FFFF00}Мерія\n{00FF00}ALT{FFFFFF} або {00FF00}/d{FFFFFF} — увійти", COLOR_WHITE, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z + 0.5, 10.0);
    CreateDynamicPickup(1318, 23, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z, MAYOR_VW, MAYOR_INT_ID);
    CreateDynamic3DTextLabel("{FFFF00}Вихід на вулицю\n{00FF00}ALT{FFFFFF} або {00FF00}/d", COLOR_WHITE, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z + 0.5, 10.0, INVALID_PLAYER_ID, INVALID_VEHICLE_ID, 0, MAYOR_VW, MAYOR_INT_ID);
    CreateDynamic3DTextLabel("{FFFF00}Віконце №3\n{FFFFFF}Видача паспортів\n{AAAAAA}Галина Петрівна", COLOR_WHITE, Points[PT_MAYOR_DESK][ptX], Points[PT_MAYOR_DESK][ptY], Points[PT_MAYOR_DESK][ptZ] + 0.8, 10.0, INVALID_PLAYER_ID, INVALID_VEHICLE_ID, 0, MAYOR_VW, MAYOR_INT_ID);

    // --- Смітники ---
    for(new d = 0; d < MAX_DUMPSTERS; d++)
    {
        CreateDynamicObject(1344, DumpsterPos[d][0], DumpsterPos[d][1], DumpsterPos[d][2], 0.0, 0.0, DumpsterPos[d][3], 0, 0);
        CreateDynamic3DTextLabel("{AAAAAA}Смітник\n{FFFFFF}ALT / /search — обшукати", COLOR_WHITE, DumpsterPos[d][0], DumpsterPos[d][1], DumpsterPos[d][2] + 1.2, 6.0);
    }

    ServerTimer = SetTimer("Server_Tick", 1000, true);
    return 1;
}

public OnGameModeExit()
{
    KillTimer(ServerTimer);
    for(new i = 0, j = GetPlayerPoolSize(); i <= j; i++)
    {
        if(IsPlayerConnected(i) && PlayerInfo[i][pLogged]) Account_Save(i);
    }
    db_close(Database);
    return 1;
}

forward Server_Tick();
public Server_Tick()
{
    for(new i = 0, j = GetPlayerPoolSize(); i <= j; i++)
    {
        if(!IsPlayerConnected(i) || !PlayerInfo[i][pLogged]) continue;
        Rent_Tick(i);
        QuestTimer_Tick(i);
        Action_Tick(i);
    }
    return 1;
}

// =============================================================================
// 12. ПОДІЇ ГРАВЦІВ
// =============================================================================
public OnPlayerConnect(playerid)
{
    ResetPlayerData(playerid);
    Account_Check(playerid);
    SetPlayerColor(playerid, 0xFFFFFF00);
    return 1;
}

public OnPlayerDisconnect(playerid, reason)
{
    if(PlayerInfo[playerid][pLogged]) Account_Save(playerid);
    Rent_End(playerid);
    Quest_ClearMarkers(playerid);
    PlayerInfo[playerid][pLogged] = false;
    return 1;
}

public OnPlayerRequestClass(playerid, classid)
{
    if(PlayerInfo[playerid][pLogged]) return 1;

    if(!PlayerInfo[playerid][pAuthShown])
    {
        PlayerInfo[playerid][pAuthShown] = true;
        TogglePlayerSpectating(playerid, true);
        SetTimerEx("Auth_Show", 300, false, "i", playerid);
    }
    return 1;
}

public OnPlayerRequestSpawn(playerid)
{
    return PlayerInfo[playerid][pLogged] ? 1 : 0;
}

public OnPlayerSpawn(playerid)
{
    if(PlayerInfo[playerid][pRegistering])
    {
        SkinPreview_Start(playerid);
        return 1;
    }
    if(!PlayerInfo[playerid][pLogged])
    {
        Kick(playerid);
        return 1;
    }

    SetPlayerInterior(playerid, 0);
    SetPlayerVirtualWorld(playerid, 0);
    SetPlayerSkin(playerid, PlayerInfo[playerid][pSkin]);
    SetCameraBehindPlayer(playerid);
    TogglePlayerControllable(playerid, true);
    GiveMoney(playerid, 0);
    PreloadAnimations(playerid);
    Quest_Resume(playerid);
    return 1;
}

public OnPlayerDeath(playerid, killerid, reason)
{
    Action_Stop(playerid);
    return 1;
}

public OnPlayerText(playerid, text[])
{
    if(!PlayerInfo[playerid][pLogged]) return 0;
    new msg[144];
    format(msg, sizeof(msg), "%s говорить: %s", GetRPName(playerid), text);
    SendLocalMessage(playerid, 20.0, COLOR_WHITE, msg);
    SetPlayerChatBubble(playerid, text, COLOR_WHITE, 20.0, 5000);
    return 0;
}

public OnPlayerKeyStateChange(playerid, newkeys, oldkeys)
{
    if((newkeys & KEY_WALK) && !(oldkeys & KEY_WALK))
    {
        HandleAction(playerid);
    }
    return 1;
}

public OnPlayerStateChange(playerid, newstate, oldstate)
{
    if(newstate == PLAYER_STATE_DRIVER)
    {
        new veh = GetPlayerVehicleID(playerid);
        new owner = RentOwner[veh];
        if(owner != INVALID_PLAYER_ID && owner != playerid)
        {
            RemovePlayerFromVehicle(playerid);
            SendClientMessage(playerid, COLOR_RED, "Цей мопед орендований іншим гравцем. Прокат — біля вокзалу (/rent).");
        }
    }
    return 1;
}

public OnVehicleDeath(vehicleid, killerid)
{
    new owner = RentOwner[vehicleid];
    if(owner != INVALID_PLAYER_ID)
    {
        Rent_End(owner, "[Оренда] Мопед розбито. Оренду завершено.");
    }
    return 1;
}

public OnPlayerPickUpDynamicPickup(playerid, STREAMER_TAG_PICKUP:pickupid)
{
    if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_BOTTLES) return 1;

    for(new i = 0; i < MAX_BOTTLES; i++)
    {
        if(BottlePickup[playerid][i] == STREAMER_TAG_PICKUP:0 || BottlePickup[playerid][i] != pickupid) continue;

        DestroyDynamicPickup(BottlePickup[playerid][i]);
        BottlePickup[playerid][i] = STREAMER_TAG_PICKUP:0;
        if(BottleIcon[playerid][i] != STREAMER_TAG_MAP_ICON:0)
        {
            DestroyDynamicMapIcon(BottleIcon[playerid][i]);
            BottleIcon[playerid][i] = STREAMER_TAG_MAP_ICON:0;
        }

        PlayerInfo[playerid][pQuestStep] |= (1 << i);
        new count = CountBits(PlayerInfo[playerid][pQuestStep]);
        PlayerPlaySound(playerid, 1150, 0.0, 0.0, 0.0);

        if(count >= MAX_BOTTLES)
        {
            SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «П'ять пляшок! Та в тебе талант! Тримай на хліб.»");
            Quest_Complete(playerid, 50);
        }
        else
        {
            new msg[64];
            format(msg, sizeof(msg), "Пляшка знайдена! Зібрано: %d/%d", count, MAX_BOTTLES);
            SendClientMessage(playerid, COLOR_GREEN, msg);
        }
        break;
    }
    return 1;
}

public OnPlayerEnterCheckpoint(playerid)
{
    if(!PlayerInfo[playerid][pLogged]) return 1;

    new q = PlayerInfo[playerid][pQuest];
    new bool:active = (PlayerInfo[playerid][pQuestActive] != 0);
    new msg[144];

    switch(PlayerInfo[playerid][pCheckpoint])
    {
        case PT_VITALIK:
        {
            if(GetPlayerState(playerid) == PLAYER_STATE_ONFOOT) Vitalik_Talk(playerid);
            else SendClientMessage(playerid, COLOR_GREY, "Вийдіть з транспорту і поговоріть з Віталіком (ALT або /d).");
        }
        case PT_RENT: SendClientMessage(playerid, COLOR_YELLOW, "Прокат мопедів: натисніть ALT або введіть /rent");
        case PT_RECYCLE:
        {
            if(active && q == Q_RECYCLE)
            {
                SendClientMessage(playerid, COLOR_WHITE, "Приймальник Борис: «П'ять пляшок? Даю 70 гривень, і то з доброти душевної.»");
                ShowPlayerDialog(playerid, DIALOG_Q_RECYCLE, DIALOG_STYLE_LIST, "{FFFF00}Що скажете Борису?",
                    "Посваритися: «Це ж елітне скло!»\nМовчки погодитись на 70 грн\nРозповісти Борису анекдот", "Обрати", "");
            }
        }
        case PT_SHAWARMA:
        {
            if(active && q == Q_SHAWARMA && PlayerInfo[playerid][pQuestStep] == 0)
            {
                PlayerInfo[playerid][pQuestStep] = 1;
                PlayerInfo[playerid][pQuestTimer] = SHAWARMA_TIME;
                SendClientMessage(playerid, COLOR_YELLOW, "Ашот: «Тримай, брат! Гроув-стріт, будинок з зеленим парканом. Лети, поки гаряча!»");
                Quest_UpdateMarkers(playerid);
            }
        }
        case PT_GROVE:
        {
            if(active && q == Q_SHAWARMA && PlayerInfo[playerid][pQuestStep] == 1)
            {
                new reward = 300;
                if(PlayerInfo[playerid][pQuestTimer] >= SHAWARMA_TIP)
                {
                    reward += 150;
                    SendClientMessage(playerid, COLOR_YELLOW, "Клієнт: «Ще гаряча! Оце сервіс!» — і дав вам 150 грн чайових.");
                }
                else SendClientMessage(playerid, COLOR_YELLOW, "Клієнт: «Ледь тепла... Ну добре, дякую.»");
                Quest_Complete(playerid, reward);
            }
        }
        case PT_STAGE:
        {
            if(active && q == Q_MUSIC) SendClientMessage(playerid, COLOR_YELLOW, "Ви на сцені! Почати концерт: ALT або /sing");
        }
        case PT_PIER:
        {
            if(active && q == Q_FISH) SendClientMessage(playerid, COLOR_YELLOW, "Гарне місце для риболовлі. Закинути вудку: ALT або /fish");
        }
        case PT_WATER:
        {
            if(active && q == Q_SWIM)
            {
                SendClientMessage(playerid, COLOR_YELLOW, "Віталік (здалеку): «Нарешті! Тепер від тебе пахне... морем. Це прогрес!»");
                Quest_Complete(playerid, 100);
            }
        }
        case PT_LOTTERY:
        {
            if(active && q == Q_LOTTERY)
            {
                ShowPlayerDialog(playerid, DIALOG_Q_LOTTERY, DIALOG_STYLE_LIST, "{FFFF00}Касирка: «Квиток виграшний! 1500 грн!»",
                    "Забрати 1500 грн\nРизикнути: подвоїти або нічого!", "Обрати", "");
            }
        }
        case PT_MAYOR_DOOR: SendClientMessage(playerid, COLOR_YELLOW, "Мерія: натисніть ALT або /d, щоб увійти.");
        case PT_MAYOR_DESK:
        {
            if(active && q == Q_MAYOR)
            {
                ShowInfo(playerid, "{FFFF00}Віконце №3", "{FFFFFF}Галина Петрівна (не відриваючись від чаю):\n\n«Паспорт? Обід у мене. А, ні, вже не обід...\nА фото 3х4 у вас є? Нема?! То й паспорта нема!»");
                Quest_Complete(playerid, 50);
            }
            else if(active && q == Q_PHOTO && PlayerInfo[playerid][pQuestStep] == 1)
            {
                ShowPlayerDialog(playerid, DIALOG_Q_AGE, DIALOG_STYLE_INPUT, "{FFFF00}Анкета на паспорт",
                    "{FFFFFF}Галина Петрівна: «Фото є... Ох, і вигляд у вас. Ну добре.\nСкільки вам років?»\n\n{AAAAAA}Вкажіть вік персонажа (від 18 до 80):", "Далі", "");
            }
        }
        case PT_PHOTO:
        {
            if(active && q == Q_PHOTO && PlayerInfo[playerid][pQuestStep] == 0)
            {
                if(GetPlayerState(playerid) != PLAYER_STATE_ONFOOT) SendClientMessage(playerid, COLOR_GREY, "Вийдіть з транспорту — фотографують тільки пішки.");
                else Action_Start(playerid, ACT_PHOTO);
            }
        }
        case PT_BURGER:
        {
            if(active && q == Q_INTERVIEW)
            {
                PlayerInfo[playerid][pQuestStep] = 0;
                PlayerInfo[playerid][pQuestExtra] = 0;
                Interview_Show(playerid);
            }
        }
        case PT_DELIVERY_1, PT_DELIVERY_2, PT_DELIVERY_3:
        {
            if(active && q == Q_SHIFT)
            {
                new step = PlayerInfo[playerid][pQuestStep];
                if(PlayerInfo[playerid][pQuestTimer] > 0)
                {
                    PlayerInfo[playerid][pQuestExtra] += 100;
                    format(msg, sizeof(msg), "Замовлення доставлено %s вчасно! +100 грн чайових.", DeliveryNames[step]);
                }
                else format(msg, sizeof(msg), "Замовлення доставлено %s (без чайових).", DeliveryNames[step]);
                SendClientMessage(playerid, COLOR_GREEN, msg);

                step++;
                if(step >= 3)
                {
                    format(msg, sizeof(msg), "Менеджер: «Зміна закрита! Зарплата 400 грн + чайові %d грн.»", PlayerInfo[playerid][pQuestExtra]);
                    SendClientMessage(playerid, COLOR_YELLOW, msg);
                    Quest_Complete(playerid, 400 + PlayerInfo[playerid][pQuestExtra]);
                }
                else
                {
                    PlayerInfo[playerid][pQuestStep] = step;
                    PlayerInfo[playerid][pQuestTimer] = SHIFT_TIME;
                    format(msg, sizeof(msg), "Наступне замовлення: %s", DeliveryNames[step]);
                    SendClientMessage(playerid, COLOR_WHITE, msg);
                    Account_Save(playerid);
                    Quest_UpdateMarkers(playerid);
                }
            }
        }
        case PT_DUMPSTER:
        {
            if(active && q == Q_DUMPSTER) SendClientMessage(playerid, COLOR_YELLOW, "Смітник! Обшукати: ALT або /search");
        }
    }
    return 1;
}

// =============================================================================
// 13. ДІАЛОГИ
// =============================================================================
public OnDialogResponse(playerid, dialogid, response, listitem, inputtext[])
{
    switch(dialogid)
    {
        case DIALOG_LOGIN:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Ви вийшли з сервера. Чекаємо на вас знову!");
                KickWithDelay(playerid);
                return 1;
            }
            if(strlen(inputtext) == 0)
            {
                ShowLoginDialog(playerid, "Введіть пароль!");
                return 1;
            }

            new hash[65];
            SHA256_PassHash(inputtext, PlayerInfo[playerid][pSalt], hash, sizeof(hash));
            if(strcmp(hash, PlayerInfo[playerid][pHash], false) != 0)
            {
                PlayerInfo[playerid][pLoginTries]++;
                if(PlayerInfo[playerid][pLoginTries] >= MAX_LOGIN_TRIES)
                {
                    SendClientMessage(playerid, COLOR_RED, "Ви ввели неправильний пароль занадто багато разів.");
                    KickWithDelay(playerid);
                    return 1;
                }
                ShowLoginDialog(playerid, "Неправильний пароль!");
                return 1;
            }

            PlayerInfo[playerid][pLogged] = true;
            new msg[144];
            format(msg, sizeof(msg), "З поверненням, %s! Баланс: %d грн. Квестів виконано: %d/%d.",
                GetRPName(playerid), PlayerInfo[playerid][pMoney], PlayerInfo[playerid][pQuest], MAX_QUESTS);
            SendClientMessage(playerid, COLOR_GREEN, msg);
            SendClientMessage(playerid, COLOR_GREY, "Меню: /menu. Команди: /help. Дія біля NPC: ALT або /d");

            SetSpawnInfo(playerid, 0, PlayerInfo[playerid][pSkin], SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_A, 0, 0, 0, 0, 0, 0);
            TogglePlayerSpectating(playerid, false);
            return 1;
        }

        case DIALOG_REGISTER:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Реєстрацію скасовано.");
                KickWithDelay(playerid);
                return 1;
            }
            new len = strlen(inputtext);
            if(len < PASS_MIN_LEN || len > PASS_MAX_LEN)
            {
                ShowRegisterDialog(playerid, "Неправильна довжина пароля!");
                return 1;
            }
            format(PlayerInfo[playerid][pTempPass], PASS_MAX_LEN + 1, "%s", inputtext);
            ShowPlayerDialog(playerid, DIALOG_REGISTER_CONFIRM, DIALOG_STYLE_PASSWORD, "{00FF00}Реєстрація [2/4]",
                "{FFFFFF}Повторіть пароль, щоб не помилитися:", "Далі", "Назад");
            return 1;
        }

        case DIALOG_REGISTER_CONFIRM:
        {
            if(!response)
            {
                ShowRegisterDialog(playerid);
                return 1;
            }
            if(strlen(inputtext) == 0 || strcmp(inputtext, PlayerInfo[playerid][pTempPass], false) != 0)
            {
                PlayerInfo[playerid][pTempPass][0] = '\0';
                ShowRegisterDialog(playerid, "Паролі не збігаються! Спробуйте ще раз.");
                return 1;
            }
            ShowPlayerDialog(playerid, DIALOG_SEX, DIALOG_STYLE_LIST, "{00FF00}Реєстрація [3/4]: стать персонажа",
                "Чоловіча\nЖіноча", "Обрати", "Назад");
            return 1;
        }

        case DIALOG_SEX:
        {
            if(!response)
            {
                PlayerInfo[playerid][pTempPass][0] = '\0';
                ShowRegisterDialog(playerid);
                return 1;
            }
            PlayerInfo[playerid][pSex] = (listitem == 0) ? 1 : 2;
            PlayerInfo[playerid][pSkinIndex] = 0;
            PlayerInfo[playerid][pRegistering] = true;

            SetSpawnInfo(playerid, 0, GetRegSkin(playerid, 0), SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_A, 0, 0, 0, 0, 0, 0);
            TogglePlayerSpectating(playerid, false); // -> OnPlayerSpawn -> SkinPreview_Start
            SendClientMessage(playerid, COLOR_YELLOW, "Реєстрація [4/4]: оберіть, яким бомжем ви почнете свій шлях.");
            return 1;
        }

        case DIALOG_SKIN:
        {
            if(!response)
            {
                ShowSkinDialog(playerid);
                return 1;
            }
            switch(listitem)
            {
                case 0: PlayerInfo[playerid][pSkinIndex] = (PlayerInfo[playerid][pSkinIndex] + 1) % 3;
                case 1: PlayerInfo[playerid][pSkinIndex] = (PlayerInfo[playerid][pSkinIndex] + 2) % 3;
                default:
                {
                    Registration_Finish(playerid);
                    return 1;
                }
            }
            SetPlayerSkin(playerid, GetRegSkin(playerid, PlayerInfo[playerid][pSkinIndex]));
            ShowSkinDialog(playerid);
            return 1;
        }

        case DIALOG_MENU:
        {
            if(!response) return 1;
            switch(listitem)
            {
                case 0: Quest_ShowCurrent(playerid);
                case 1: ShowRentHelp(playerid);
                case 2: ShowStats(playerid);
                case 3: ShowHelp(playerid);
            }
            return 1;
        }

        case DIALOG_RENT:
        {
            if(!response) return 1;
            if(!IsNearPoint(playerid, PT_RENT, 4.0))
            {
                SendClientMessage(playerid, COLOR_GREY, "Ви відійшли від пункту прокату.");
                return 1;
            }
            if(listitem >= 0 && listitem < sizeof(RentTariffs)) Rent_Start(playerid, listitem);
            return 1;
        }

        case DIALOG_QUEST_OFFER:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Квест відкладено. Повернутися до нього: /quest");
                return 1;
            }
            if(!PlayerInfo[playerid][pQuestActive] && PlayerInfo[playerid][pQuest] < MAX_QUESTS) Quest_Accept(playerid);
            return 1;
        }

        case DIALOG_Q_RECYCLE:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_RECYCLE) return 1;
            new reward;
            switch(response ? listitem : 1)
            {
                case 0:
                {
                    if(random(2))
                    {
                        reward = 130;
                        SendClientMessage(playerid, COLOR_WHITE, "Борис зніяковів: «Ну... елітне, то елітне». Він дав вам 130 грн.");
                    }
                    else
                    {
                        reward = 40;
                        SendClientMessage(playerid, COLOR_WHITE, "Борис образився: «Елітне?! Тримай 40 і не ображай мене більше!»");
                    }
                }
                case 1:
                {
                    reward = 70;
                    SendClientMessage(playerid, COLOR_WHITE, "Борис задоволено відрахував 70 грн. Здається, він знову вас надурив.");
                }
                default:
                {
                    SendClientMessage(playerid, COLOR_WHITE, "Ви: «Заходить бомж у банк... і виходить звідти директором!»");
                    if(random(100) < 60)
                    {
                        reward = 200;
                        SendClientMessage(playerid, COLOR_WHITE, "Борис регоче до сліз і дає вам 200 грн: «За настрій!»");
                    }
                    else
                    {
                        reward = 50;
                        SendClientMessage(playerid, COLOR_WHITE, "Борис не зрозумів жарту. Дав 50 грн, щоб ви пішли.");
                    }
                }
            }
            Quest_Complete(playerid, reward);
            return 1;
        }

        case DIALOG_Q_QUIZ:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_QUIZ) return 1;
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Іспит відкладено. Продовжити: /quest");
                return 1;
            }
            new idx = PlayerInfo[playerid][pQuestStep];
            if(listitem == QuizData[idx][qzRight])
            {
                idx++;
                if(idx >= sizeof(QuizData))
                {
                    SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «Відмінно! Ти знаєш закони краще за половину цього міста.»");
                    Quest_Complete(playerid, 200);
                    return 1;
                }
                PlayerInfo[playerid][pQuestStep] = idx;
                SendClientMessage(playerid, COLOR_GREEN, "Віталік: «Правильно! Наступне питання...»");
            }
            else
            {
                PlayerInfo[playerid][pQuestStep] = 0;
                SendClientMessage(playerid, COLOR_RED, "Віталік: «Неправильно! Вчи матчастину. Починаємо спочатку!»");
            }
            Quiz_Show(playerid);
            return 1;
        }

        case DIALOG_Q_LOTTERY:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_LOTTERY) return 1;
            if(!response || listitem == 0)
            {
                SendClientMessage(playerid, COLOR_WHITE, "Касирка відрахувала 1500 грн і додала жуйку «на щастя».");
                Quest_Complete(playerid, 1500);
                return 1;
            }
            SendClientMessage(playerid, COLOR_WHITE, "Касирка крутить барабан... Усі в магазині затамували подих...");
            if(random(2))
            {
                SendClientMessage(playerid, COLOR_GREEN, "ДЖЕКПОТ! Ви подвоїли виграш — 3000 грн!");
                Quest_Complete(playerid, 3000);
            }
            else
            {
                SendClientMessage(playerid, COLOR_RED, "Нічого... Касирка співчутливо дала вам жуйку. Віталік би сказав: «Буває».");
                Quest_Complete(playerid, 0);
            }
            return 1;
        }

        case DIALOG_Q_AGE:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_PHOTO) return 1;
            new age = strval(inputtext);
            if(!response || age < 18 || age > 80)
            {
                ShowPlayerDialog(playerid, DIALOG_Q_AGE, DIALOG_STYLE_INPUT, "{FFFF00}Анкета на паспорт",
                    "{FFFFFF}Галина Петрівна: «Не морочте мені голову!»\n\n{AAAAAA}Вкажіть вік персонажа (від 18 до 80):", "Далі", "");
                return 1;
            }
            PlayerInfo[playerid][pAge] = age;
            PlayerInfo[playerid][pPassport] = 1;
            SendClientMessage(playerid, COLOR_YELLOW, "Галина Петрівна: «Ось ваш паспорт. Наступний!» Ви тепер повноправний громадянин!");
            Quest_Complete(playerid, 200);
            return 1;
        }

        case DIALOG_Q_INTERVIEW:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_INTERVIEW) return 1;
            if(!response)
            {
                PlayerInfo[playerid][pQuestStep] = 0;
                PlayerInfo[playerid][pQuestExtra] = 0;
                SendClientMessage(playerid, COLOR_GREY, "Ви втекли зі співбесіди. Зайдіть у маркер ще раз, коли наберетесь сміливості.");
                return 1;
            }
            new idx = PlayerInfo[playerid][pQuestStep];
            if(listitem < 0 || listitem > 2) listitem = 0;
            PlayerInfo[playerid][pQuestExtra] += InterviewData[idx][ivScore][listitem];
            idx++;
            if(idx < sizeof(InterviewData))
            {
                PlayerInfo[playerid][pQuestStep] = idx;
                Interview_Show(playerid);
                return 1;
            }

            new score = PlayerInfo[playerid][pQuestExtra];
            if(score >= 7) SendClientMessage(playerid, COLOR_YELLOW, "Менеджер: «Ти найкращий кандидат за весь рік! Береш першу зміну?»");
            else if(score >= 4) SendClientMessage(playerid, COLOR_YELLOW, "Менеджер: «Дивно, але ти нам підходиш. Ласкаво просимо!»");
            else SendClientMessage(playerid, COLOR_YELLOW, "Менеджер: «Жахливо... Але Віталік просив. Беру тебе.»");
            Quest_Complete(playerid, 100 + score * 40);
            return 1;
        }

        case DIALOG_Q_FINAL:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_FINAL) return 1;
            FinalSkin_Show(playerid);
            return 1;
        }

        case DIALOG_Q_FINAL_SKIN:
        {
            if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_FINAL) return 1;
            if(!response)
            {
                FinalSkin_Show(playerid);
                return 1;
            }
            if(listitem >= 1 && listitem <= 3)
            {
                new skin = (PlayerInfo[playerid][pSex] == 1) ? FinalMaleSkins[listitem - 1] : FinalFemaleSkins[listitem - 1];
                PlayerInfo[playerid][pSkin] = skin;
                SetPlayerSkin(playerid, skin);
                SetSpawnInfo(playerid, 0, skin, SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_A, 0, 0, 0, 0, 0, 0);
                SendClientMessage(playerid, COLOR_YELLOW, "Віталік: «Новий одяг — нове життя. Але не забувай, звідки ти прийшов.»");
            }
            else SendClientMessage(playerid, COLOR_YELLOW, "Віталік (зі сльозами): «Справжній бомж! Я в тобі не помилився.»");

            Quest_Complete(playerid, 5000);

            new msg[144];
            format(msg, sizeof(msg), "[Bereza RP] %s пройшов шлях від бомжа до громадянина! Вітаємо!", GetRPName(playerid));
            SendClientMessageToAll(COLOR_ORANGE, msg);
            return 1;
        }
    }
    return 0;
}

// =============================================================================
// 14. ВІКНА МЕНЮ
// =============================================================================
stock ShowStats(playerid)
{
    new text[600], rent[32], age[16];
    if(PlayerInfo[playerid][pRentVeh] != INVALID_VEHICLE_ID) format(rent, sizeof(rent), "так (ще %d хв)", PlayerInfo[playerid][pRentLeft] / 60 + 1);
    else format(rent, sizeof(rent), "ні");
    if(PlayerInfo[playerid][pAge] > 0) format(age, sizeof(age), "%d", PlayerInfo[playerid][pAge]);
    else format(age, sizeof(age), "невідомо");

    format(text, sizeof(text),
        "{FFFFFF}Ім'я: {FFFF00}%s\n{FFFFFF}Номер акаунту: {FFFF00}%d\n{FFFFFF}Стать: {FFFF00}%s\n{FFFFFF}Вік: {FFFF00}%s\n{FFFFFF}Гроші: {00FF00}%d грн\n{FFFFFF}Паспорт: {FFFF00}%s\n{FFFFFF}Квести: {FFFF00}%d/%d\n{FFFFFF}Оренда мопеда: {FFFF00}%s",
        GetRPName(playerid), PlayerInfo[playerid][pID],
        (PlayerInfo[playerid][pSex] == 1) ? ("чоловіча") : ("жіноча"),
        age,
        PlayerInfo[playerid][pMoney],
        PlayerInfo[playerid][pPassport] ? ("є") : ("немає"),
        PlayerInfo[playerid][pQuest], MAX_QUESTS, rent);
    ShowInfo(playerid, "{00FF00}Статистика", text);
}

stock ShowHelp(playerid)
{
    new text[900];
    strcat(text, "{FFFF00}Основне:\n{FFFFFF}/menu — головне меню\n/quest — поточний квест\n/stats — статистика\n");
    strcat(text, "/d — дія (те саме, що ALT; зручно на телефоні)\n\n");
    strcat(text, "{FFFF00}Транспорт:\n{FFFFFF}/rent — орендувати мопед (біля прокату)\n/unrent — повернути мопед\n\n");
    strcat(text, "{FFFF00}Квестові дії:\n{FFFFFF}/sing — співати, /fish — рибалити, /search — обшукати смітник\n\n");
    strcat(text, "{FFFF00}RP-команди:\n{FFFFFF}/me — дія, /do — опис, /try — спроба, /b — OOC-чат");
    ShowInfo(playerid, "{00FF00}Допомога", text);
}

stock ShowRentHelp(playerid)
{
    new text[400], line[64];
    strcat(text, "{FFFFFF}Прокат мопедів «Бджілка» знаходиться біля вокзалу,\nпоруч із бомжем Віталіком.\n\n{FFFF00}Тарифи:\n{FFFFFF}");
    for(new i = 0; i < sizeof(RentTariffs); i++)
    {
        format(line, sizeof(line), "%d хв — %d грн\n", RentTariffs[i][trMinutes], RentTariffs[i][trPrice]);
        strcat(text, line);
    }
    strcat(text, "\nОрендувати: ALT біля прокату або /rent\nПовернути: /unrent");
    ShowInfo(playerid, "{FFFF00}Оренда мопеда", text);
}

// =============================================================================
// 15. КОМАНДИ
// =============================================================================
forward OnPlayerCommandReceived(playerid, cmd[], params[], flags);
forward OnPlayerCommandPerformed(playerid, cmd[], params[], result, flags);

public OnPlayerCommandReceived(playerid, cmd[], params[], flags)
{
    if(!PlayerInfo[playerid][pLogged])
    {
        SendClientMessage(playerid, COLOR_GREY, "Спочатку увійдіть в акаунт.");
        return 0;
    }
    return 1;
}

public OnPlayerCommandPerformed(playerid, cmd[], params[], result, flags)
{
    if(result == -1)
    {
        SendClientMessage(playerid, COLOR_GREY, "Невідома команда. Список команд: /help");
        return 0;
    }
    return 1;
}

cmd:menu(playerid, params[])
{
    ShowPlayerDialog(playerid, DIALOG_MENU, DIALOG_STYLE_LIST, "{00FF00}Головне меню",
        "Квести\nОренда мопеда\nСтатистика\nДопомога і команди", "Обрати", "Закрити");
    return 1;
}

cmd:help(playerid, params[])
{
    ShowHelp(playerid);
    return 1;
}

cmd:stats(playerid, params[])
{
    ShowStats(playerid);
    return 1;
}

cmd:quest(playerid, params[])
{
    Quest_ShowCurrent(playerid);
    return 1;
}

cmd:d(playerid, params[])
{
    if(!HandleAction(playerid)) SendClientMessage(playerid, COLOR_GREY, "Поруч немає нічого, з чим можна взаємодіяти.");
    return 1;
}

cmd:rent(playerid, params[])
{
    if(!IsNearPoint(playerid, PT_RENT, 4.0))
    {
        SendClientMessage(playerid, COLOR_GREY, "Ви не біля прокату мопедів. Він знаходиться на вокзалі, біля Віталіка.");
        return 1;
    }
    ShowRentDialog(playerid);
    return 1;
}

cmd:unrent(playerid, params[])
{
    if(!Rent_End(playerid, "Ви повернули мопед у прокат. Дякуємо, що обрали «Бджілку»!"))
        SendClientMessage(playerid, COLOR_GREY, "У вас немає орендованого мопеда.");
    return 1;
}

cmd:sing(playerid, params[])
{
    if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_MUSIC || !IsNearPoint(playerid, PT_STAGE, 5.0))
        return SendClientMessage(playerid, COLOR_GREY, "Співати можна на сцені на площі Першинг під час квесту «Вуличний музикант».");
    Action_Start(playerid, ACT_SING);
    return 1;
}

cmd:fish(playerid, params[])
{
    if(!PlayerInfo[playerid][pQuestActive] || PlayerInfo[playerid][pQuest] != Q_FISH || !IsNearPoint(playerid, PT_PIER, 8.0))
        return SendClientMessage(playerid, COLOR_GREY, "Рибалити можна на пірсі Санта-Марія під час квесту «Золота рибка».");
    Action_Start(playerid, ACT_FISH);
    return 1;
}

cmd:search(playerid, params[])
{
    if(!HandleAction(playerid)) SendClientMessage(playerid, COLOR_GREY, "Поруч немає смітника.");
    return 1;
}

cmd:me(playerid, params[])
{
    if(isnull(params)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /me [дія]");
    new msg[144];
    format(msg, sizeof(msg), "* %s %s", GetRPName(playerid), params);
    SendLocalMessage(playerid, 20.0, COLOR_PURPLE, msg);
    return 1;
}

cmd:do(playerid, params[])
{
    if(isnull(params)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /do [опис]");
    new msg[144];
    format(msg, sizeof(msg), "* %s (( %s ))", params, GetRPName(playerid));
    SendLocalMessage(playerid, 20.0, COLOR_PURPLE, msg);
    return 1;
}

cmd:try(playerid, params[])
{
    if(isnull(params)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /try [дія]");
    new msg[144];
    format(msg, sizeof(msg), "* %s %s | %s", GetRPName(playerid), params, random(2) ? ("{33AA33}Вдало") : ("{FF3333}Невдало"));
    SendLocalMessage(playerid, 20.0, COLOR_PURPLE, msg);
    return 1;
}

cmd:b(playerid, params[])
{
    if(isnull(params)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /b [текст]");
    new msg[144];
    format(msg, sizeof(msg), "(( %s: %s ))", GetRPName(playerid), params);
    SendLocalMessage(playerid, 20.0, COLOR_GREY, msg);
    return 1;
}

// Допомагає підправити координати точок: стань у потрібне місце і введи /mypos
cmd:mypos(playerid, params[])
{
    new Float:x, Float:y, Float:z, Float:a, msg[144];
    GetPlayerPos(playerid, x, y, z);
    GetPlayerFacingAngle(playerid, a);
    format(msg, sizeof(msg), "Позиція: %.4f, %.4f, %.4f | кут: %.2f | інтер'єр: %d | світ: %d", x, y, z, a, GetPlayerInterior(playerid), GetPlayerVirtualWorld(playerid));
    SendClientMessage(playerid, COLOR_WHITE, msg);
    printf("[mypos] %s: %.4f, %.4f, %.4f, %.2f", GetName(playerid), x, y, z, a);
    return 1;
}
