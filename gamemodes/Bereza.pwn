// =============================================================================
//                    BEREZA ROLE PLAY — ГОЛОВНИЙ ФАЙЛ МОДУ
// =============================================================================

#include <a_samp>
#include <Pawn.CMD>

#define SSCANF_NO_NATIVE_INIT
#include <sscanf2>

#include <streamer>
#include <a_mysql>
new MySQL:dbHandle;

#pragma warning disable 239

// =============================================================================
// 1. КОНСТАНТИ ДІАЛОГІВ ТА КОЛЬОРИ
// =============================================================================
#define DIALOG_REGISTER     1
#define DIALOG_LOGIN        2
#define DIALOG_MM           3
#define DIALOG_STATS        4
#define DIALOG_SEX          5
#define DIALOG_SKIN_SELECT  6
#define DIALOG_QUESTS       7
#define DIALOG_QUEST_INFO   8
#define DIALOG_RENT_FAGGIO  9
#define DIALOG_MAKEBIZ      10
#define DIALOG_BIZ_MENU     11
#define DIALOG_GPS_MAIN     12
#define DIALOG_GPS_BIZ_CATS 13
#define DIALOG_GPS_ATB      14
#define DIALOG_GPS_MAIN_LOCS 15

#define COLOR_GREEN         0x33AA33FF
#define COLOR_WHITE         0xFFFFFFFF
#define COLOR_YELLOW        0xFFFF00FF
#define COLOR_GREY          0xAFAFAFFF
#define COLOR_RED           0xFF0000FF

#define MAX_BIZ             150

// Координати Мерії
#define MAYOR_ENT_X         1481.0542
#define MAYOR_ENT_Y         -1771.8157
#define MAYOR_ENT_Z         18.7958
#define MAYOR_ENT_A         0.0

#define MAYOR_INT_X         389.8938
#define MAYOR_INT_Y         173.8116
#define MAYOR_INT_Z         1008.3828
#define MAYOR_INT_A         90.0
#define MAYOR_INT_ID        3

new const MaleSkins[3]   = {78, 79, 137};
new const FemaleSkins[3] = {13, 77, 196};

enum pInfo
{
    pID,
    pPassword[64],
    pSex,
    pSkin,
    pSkinIndex,
    pAdmin,
    pMoney,
    bool:pLogged
}
new PlayerInfo[MAX_PLAYERS][pInfo];

new SelectedQuest[MAX_PLAYERS];

enum bInfo
{
    bID,
    bName[64],
    bOwner[MAX_PLAYER_NAME],
    bType,              // 0: АТБ, 1: Магазин одягу, 2: Магазин зброї, 3: Макдональдс
    bPrice,
    Float:bEntX,
    Float:bEntY,
    Float:bEntZ,
    Float:bEntA,
    Float:bPickX,
    Float:bPickY,
    Float:bPickZ,
    bPickupID,
    Text3D:bTextID
}
new BizInfo[MAX_BIZ][bInfo];
new TotalBiz = 0;
new TempBizPrice[MAX_PLAYERS];
new SelectedBiz[MAX_PLAYERS];
new GPS_ATB_Index[MAX_PLAYERS][MAX_BIZ]; // Масив для збереження індексів АТБ у GPS

enum atbData
{
    atbName[64],
    Float:atbX,
    Float:atbY,
    Float:atbZ,
    Float:atbA,
    atbPrice
}

new const DefaultATBs[][atbData] = 
{
    // Без вказування конкретних міст у назві
    {"АТБ", -2594.0473, 457.8992, 14.6093, 180.0, 5000000},
    {"АТБ", 1834.72, -1842.54, 13.57, 90.0, 5000000},
    {"АТБ", 1000.65, -921.09, 42.32, 0.0, 5000000},
    {"АТБ", 2070.6267, -1793.7706, 13.5468, 270.0, 5000000},
    {"АТБ", -1620.9060, 1162.5582, 7.1875, 90.0, 5000000},
    {"АТБ", -2420.1560, 969.8496, 45.2968, 180.0, 5000000},
    {"АТБ", -126.9140, 1188.2580, 19.6187, 0.0, 5000000},
    {"АТБ", 2194.9409, 1991.0838, 271.4942, 270.0, 5000000},
    {"АТБ", 2637.3696, 1129.6624, 10.1796, 90.0, 5000000},
    {"АТБ", 1937.8256, 2307.1970, 10.8203, 180.0, 5000000},
    {"АТБ", 2097.6726, 2224.7014, 11.0234, 90.0, 5000000},
    {"АТБ", 1128.7321, -1270.5219, 13.5468, 0.0, 5000000},
    {"АТБ", 1352.3780, -1759.2532, 13.5078, 270.0, 5000000},
    {"АТБ", -145.8582, 1172.4093, 19.7499, 180.0, 5000000},
    {"АТБ", -2165.4995, -2416.7341, 30.8203, 90.0, 5000000},
    {"АТБ", 1310.9711, 330.0352, 19.9140, 0.0, 5000000},
    {"АТБ", 2277.9589, 51.0091, 26.4843, 180.0, 5000000}
};

// =============================================================================
// 2. ДОПОМІЖНІ ФУНКЦІЇ ДЛЯ ЗСУВУ ТА ЗБЕРЕЖЕННЯ
// =============================================================================

stock GetOffsetPos(Float:x, Float:y, Float:angle, Float:distance, &Float:outX, &Float:outY)
{
    outX = x + (distance * floatsin(-angle, degrees));
    outY = y + (distance * floatcos(-angle, degrees));
}

stock GetPlayerAccountPath(playerid, buffer[], len)
{
    new name[MAX_PLAYER_NAME];
    GetPlayerName(playerid, name, sizeof(name));
    format(buffer, len, "accounts/%s.txt", name);
}

stock GetNextAccountID()
{
    new count = 0;
    if(fexist("accounts/global_counter.txt"))
    {
        new File:f = fopen("accounts/global_counter.txt", io_read);
        if(f)
        {
            new line[32];
            fread(f, line);
            count = strval(line);
            fclose(f);
        }
    }
    count++;

    new File:f_out = fopen("accounts/global_counter.txt", io_write);
    if(f_out)
    {
        new str[32];
        format(str, sizeof(str), "%d", count);
        fwrite(f_out, str);
        fclose(f_out);
    }
    return (100000 + count);
}

stock SaveAccount(playerid)
{
    new path[64];
    GetPlayerAccountPath(playerid, path, sizeof(path));
    new File:f = fopen(path, io_write);
    if(f)
    {
        new str[256];
        format(str, sizeof(str), "%s\r\n%d\r\n%d\r\n%d\r\n%d\r\n%d", 
            PlayerInfo[playerid][pPassword], 
            PlayerInfo[playerid][pID], 
            PlayerInfo[playerid][pSex], 
            PlayerInfo[playerid][pSkin],
            PlayerInfo[playerid][pAdmin],
            GetPlayerMoney(playerid));
        fwrite(f, str);
        fclose(f);
    }
}

stock LoadAccount(playerid)
{
    new path[64];
    GetPlayerAccountPath(playerid, path, sizeof(path));
    new File:f = fopen(path, io_read);
    if(f)
    {
        new line[64];
        if(fread(f, line))
        {
            for(new i = 0; i < strlen(line); i++)
            {
                if(line[i] == '\r' || line[i] == '\n') { line[i] = '\0'; break; }
            }
            format(PlayerInfo[playerid][pPassword], 64, "%s", line);
        }

        if(fread(f, line)) PlayerInfo[playerid][pID]    = strval(line);
        if(fread(f, line)) PlayerInfo[playerid][pSex]   = strval(line);
        if(fread(f, line)) PlayerInfo[playerid][pSkin]  = strval(line);
        if(fread(f, line)) PlayerInfo[playerid][pAdmin] = strval(line);
        if(fread(f, line)) 
        {
            PlayerInfo[playerid][pMoney] = strval(line);
            ResetPlayerMoney(playerid);
            GivePlayerMoney(playerid, PlayerInfo[playerid][pMoney]);
        }
        fclose(f);
    }
}

stock CheckPassword(playerid, const input_password[])
{
    new path[64];
    GetPlayerAccountPath(playerid, path, sizeof(path));
    new File:f = fopen(path, io_read);
    if(f)
    {
        new saved_pass[64];
        if(fread(f, saved_pass))
        {
            fclose(f);
            for(new i = 0; i < strlen(saved_pass); i++)
            {
                if(saved_pass[i] == '\r' || saved_pass[i] == '\n')
                {
                    saved_pass[i] = '\0';
                    break;
                }
            }
            if(!strcmp(saved_pass, input_password, false)) return 1;
        }
        else fclose(f);
    }
    return 0;
}

stock ShowSkinSelectionDialog(playerid)
{
    new msg[256];
    format(msg, sizeof(msg),
        "{FFFFFF}Перегляд варіанта: {FFFF00}%d з 3\n\n\
        {FFFFFF}Натисніть {00FF00}'Наступний >>>'{FFFFFF}, щоб переглянути інший скін.\n\
        Натисніть {00FF00}'Вибрати'{FFFFFF}, щоб підтвердити свій вибір.",
        PlayerInfo[playerid][pSkinIndex] + 1);

    ShowPlayerDialog(playerid, DIALOG_SKIN_SELECT, DIALOG_STYLE_MSGBOX,
        "{00FF00}Вибір початкового скіна",
        msg,
        "Вибрати", "Наступний >>>");
}

stock SaveBusinesses()
{
    new File:f = fopen("accounts/businesses.txt", io_write);
    if(f)
    {
        new str[256];
        for(new i = 0; i < TotalBiz; i++)
        {
            format(str, sizeof(str), "%d|%s|%s|%d|%d|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f\r\n",
                BizInfo[i][bID],
                BizInfo[i][bName],
                BizInfo[i][bOwner],
                BizInfo[i][bType],
                BizInfo[i][bPrice],
                BizInfo[i][bEntX],
                BizInfo[i][bEntY],
                BizInfo[i][bEntZ],
                BizInfo[i][bEntA],
                BizInfo[i][bPickX],
                BizInfo[i][bPickY],
                BizInfo[i][bPickZ]);
            fwrite(f, str);
        }
        fclose(f);
    }
}

stock UpdateBizText(i)
{
    new label_str[256];
    if(!strcmp(BizInfo[i][bOwner], "The State", true))
    {
        format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {FF0000}Продається\n{FFFF00}Вартість: {00FF00}%d грн\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для меню",
            BizInfo[i][bID], BizInfo[i][bName], BizInfo[i][bPrice]);
    }
    else
    {
        format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {00FF00}%s\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для входу",
            BizInfo[i][bID], BizInfo[i][bName], BizInfo[i][bOwner]);
    }

    if(IsValidDynamic3DTextLabel(BizInfo[i][bTextID]))
    {
        UpdateDynamic3DTextLabelText(BizInfo[i][bTextID], COLOR_WHITE, label_str);
    }
}

stock CreateDefaultATBs()
{
    for(new i = 0; i < sizeof(DefaultATBs); i++)
    {
        if(TotalBiz >= MAX_BIZ) break;
        new idx = TotalBiz;

        BizInfo[idx][bID] = idx + 1;
        format(BizInfo[idx][bName], 64, "%s", DefaultATBs[i][atbName]);
        format(BizInfo[idx][bOwner], MAX_PLAYER_NAME, "The State");
        BizInfo[idx][bType] = 0;
        BizInfo[idx][bPrice] = DefaultATBs[i][atbPrice];
        BizInfo[idx][bEntX] = DefaultATBs[i][atbX];
        BizInfo[idx][bEntY] = DefaultATBs[i][atbY];
        BizInfo[idx][bEntZ] = DefaultATBs[i][atbZ];
        BizInfo[idx][bEntA] = DefaultATBs[i][atbA];

        new Float:pX, Float:pY;
        GetOffsetPos(BizInfo[idx][bEntX], BizInfo[idx][bEntY], BizInfo[idx][bEntA], 1.2, pX, pY);

        BizInfo[idx][bPickX] = pX;
        BizInfo[idx][bPickY] = pY;
        BizInfo[idx][bPickZ] = BizInfo[idx][bEntZ];

        BizInfo[idx][bPickupID] = CreateDynamicPickup(1318, 23, BizInfo[idx][bPickX], BizInfo[idx][bPickY], BizInfo[idx][bPickZ]);

        new label_str[256];
        format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {FF0000}Продається\n{FFFF00}Вартість: {00FF00}%d грн\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для меню",
            BizInfo[idx][bID], BizInfo[idx][bName], BizInfo[idx][bPrice]);

        BizInfo[idx][bTextID] = CreateDynamic3DTextLabel(label_str, COLOR_WHITE, BizInfo[idx][bPickX], BizInfo[idx][bPickY], BizInfo[idx][bPickZ] + 0.5, 10.0);

        TotalBiz++;
    }
    SaveBusinesses();
}

stock LoadBusinesses()
{
    if(!fexist("accounts/businesses.txt"))
    {
        CreateDefaultATBs();
        return 1;
    }

    new File:f = fopen("accounts/businesses.txt", io_read);
    if(f)
    {
        new line[256];
        while(fread(f, line))
        {
            if(TotalBiz >= MAX_BIZ) break;
            new i = TotalBiz;
            
            if(sscanf(line, "p<|>ds[64]s[24]ddfffffff",
                BizInfo[i][bID],
                BizInfo[i][bName],
                BizInfo[i][bOwner],
                BizInfo[i][bType],
                BizInfo[i][bPrice],
                BizInfo[i][bEntX],
                BizInfo[i][bEntY],
                BizInfo[i][bEntZ],
                BizInfo[i][bEntA],
                BizInfo[i][bPickX],
                BizInfo[i][bPickY],
                BizInfo[i][bPickZ]) == 0)
            {
                BizInfo[i][bPickupID] = CreateDynamicPickup(1318, 23, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ]);

                new label_str[256];
                if(!strcmp(BizInfo[i][bOwner], "The State", true))
                {
                    format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {FF0000}Продається\n{FFFF00}Вартість: {00FF00}%d грн\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для меню",
                        BizInfo[i][bID], BizInfo[i][bName], BizInfo[i][bPrice]);
                }
                else
                {
                    format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {00FF00}%s\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для входу",
                        BizInfo[i][bID], BizInfo[i][bName], BizInfo[i][bOwner]);
                }

                BizInfo[i][bTextID] = CreateDynamic3DTextLabel(label_str, COLOR_WHITE, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ] + 0.5, 10.0);

                TotalBiz++;
            }
        }
        fclose(f);

        if(TotalBiz == 0)
        {
            CreateDefaultATBs();
        }
    }
    return 1;
}

stock EnterBusiness(playerid, i)
{
    switch(BizInfo[i][bType])
    {
        case 0:
        {
            SetPlayerInterior(playerid, 6);
            SetPlayerVirtualWorld(playerid, i + 1);
            SetPlayerPos(playerid, -25.8884, -55.8605, 1003.5469);
            SetPlayerFacingAngle(playerid, 90.0);
        }
        case 1:
        {
            SetPlayerInterior(playerid, 3);
            SetPlayerVirtualWorld(playerid, i + 1);
            SetPlayerPos(playerid, 207.7379, -109.0199, 1005.1328);
            SetPlayerFacingAngle(playerid, 0.0);
        }
        case 2:
        {
            SetPlayerInterior(playerid, 1);
            SetPlayerVirtualWorld(playerid, i + 1);
            SetPlayerPos(playerid, 285.8000, -38.0000, 1001.5156);
            SetPlayerFacingAngle(playerid, 0.0);
        }
        case 3:
        {
            SetPlayerInterior(playerid, 9);
            SetPlayerVirtualWorld(playerid, i + 1);
            SetPlayerPos(playerid, 365.6203, -11.6033, 1001.8516);
            SetPlayerFacingAngle(playerid, 0.0);
        }
    }
    SetCameraBehindPlayer(playerid);
}

stock SetGPSDestination(playerid, Float:x, Float:y, Float:z, Float:angle, const name[])
{
    if(PlayerInfo[playerid][pAdmin] >= 1)
    {
        SetPlayerInterior(playerid, 0);
        SetPlayerVirtualWorld(playerid, 0);

        if(IsPlayerInAnyVehicle(playerid))
        {
            new veh = GetPlayerVehicleID(playerid);
            SetVehiclePos(veh, x, y, z + 1.0);
            SetVehicleZAngle(veh, angle);
        }
        else
        {
            SetPlayerPos(playerid, x, y, z + 1.0);
            SetPlayerFacingAngle(playerid, angle);
        }
        SetCameraBehindPlayer(playerid);

        new str[144];
        format(str, sizeof(str), "[GPS Admin] Ви телепортувалися до точки: %s!", name);
        SendClientMessage(playerid, COLOR_GREEN, str);
    }
    else
    {
        SetPlayerRaceCheckpoint(playerid, 1, x, y, z, 0.0, 0.0, 0.0, 3.0);
        
        new str[144];
        format(str, sizeof(str), "[GPS] Мітка встановлена: %s. Дотримуйтесь червоного маркера!", name);
        SendClientMessage(playerid, COLOR_GREEN, str);
    }
}

// =============================================================================
// 3. ІНІЦІАЛІЗАЦІЯ СЕРВЕРА
// =============================================================================
main()
{
    print("\n----------------------------------");
    print(" Bereza.RolePlay успішно запущено! ");
    print("----------------------------------\n");
}

public OnGameModeInit()
{

    dbHandle = mysql_connect("127.0.0.1", "user40236", "user40236", "45ZB9IXOfXZ0");

    LoadBusinessesFromDB();
	
    SetGameModeText("Bereza RP v1.0");

    DisableInteriorEnterExits();

    new map_cmd[] = "mapname San Andreas";
    SendRconCommand(map_cmd);

    AddPlayerClass(60, 1759.0186, -1898.1260, 13.5622, 266.4503, 0, 0, 0, 0, 0, 0);

    // Квестовий бот
    CreateActor(60, 1808.4893, -1900.0646, 13.6783, 94.7653);
    CreateDynamic3DTextLabel("{FFFF00}Квестовий персонаж Віталік\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для розмови", COLOR_WHITE, 1808.4893, -1900.0646, 14.6, 10.0);

    // Оренда мопедів
    CreateDynamicPickup(1239, 23, 1805.5000, -1900.0646, 13.5622);
    CreateDynamic3DTextLabel("{00FF00}Оренда мопеда\n{FFFFFF}Натисніть {00FF00}ALT", COLOR_WHITE, 1805.5000, -1900.0646, 14.2, 10.0);

    // Універсальний вихід з магазину (АТБ)
    CreateDynamicPickup(1318, 23, -25.8884, -55.8605, 1003.5469, -1, 6);
    CreateDynamic3DTextLabel("{FFFF00}Вихід на вулицю\n{FFFFFF}Натисніть {00FF00}ALT", COLOR_WHITE, -25.8884, -55.8605, 1004.0, 10.0, INVALID_PLAYER_ID, INVALID_VEHICLE_ID, 0, -1, 6);

    // --- МЕРІЯ ---
    // Вхід у Мерію (вулиця)
    CreateDynamicPickup(1318, 23, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z);
    CreateDynamic3DTextLabel("{FFFF00}Мерія м. Лос-Сантос\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для входу", COLOR_WHITE, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z + 0.5, 10.0);

    // Вихід з Мерії (інтер'єр)
    CreateDynamicPickup(1318, 23, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z, -1, MAYOR_INT_ID);
    CreateDynamic3DTextLabel("{FFFF00}Вихід на вулицю\n{FFFFFF}Натисніть {00FF00}ALT", COLOR_WHITE, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z + 0.5, 10.0, INVALID_PLAYER_ID, INVALID_VEHICLE_ID, 0, -1, MAYOR_INT_ID);

    LoadBusinesses();

    return 1;
}

public OnGameModeExit()
{
    for(new i = 0; i < MAX_PLAYERS; i++)
    {
        if(IsPlayerConnected(i) && PlayerInfo[i][pLogged])
        {
            SaveAccount(i);
        }
    }
    return 1;
}

// =============================================================================
// 4. ПОДІЇ ГРАВЦІВ
// =============================================================================
public OnPlayerConnect(playerid)
{
    PlayerInfo[playerid][pLogged] = false;
    PlayerInfo[playerid][pID] = 0;
    PlayerInfo[playerid][pSex] = 1;
    PlayerInfo[playerid][pSkin] = 60;
    PlayerInfo[playerid][pSkinIndex] = 0;
    PlayerInfo[playerid][pAdmin] = 0;
    PlayerInfo[playerid][pMoney] = 0;
    SelectedQuest[playerid] = 0;
    TempBizPrice[playerid] = 0;
    SelectedBiz[playerid] = -1;

    SetPlayerPos(playerid, 1759.0186, -1898.1260, 20.0);
    SetPlayerCameraPos(playerid, 1780.0, -1900.0, 25.0);
    SetPlayerCameraLookAt(playerid, 1759.0186, -1898.1260, 13.5);

    new path[64];
    GetPlayerAccountPath(playerid, path, sizeof(path));

    if(fexist(path))
    {
        ShowPlayerDialog(playerid, DIALOG_LOGIN, DIALOG_STYLE_PASSWORD,
            "{00FF00}Авторизація",
            "{FFFFFF}Вітаємо з поверненням на Bereza RP!\nВведіть ваш пароль для входу в гру:",
            "Увійти", "Вихід");
    }
    else
    {
        ShowPlayerDialog(playerid, DIALOG_REGISTER, DIALOG_STYLE_PASSWORD,
            "{00FF00}Реєстрація акаунту",
            "{FFFFFF}Ласкаво просимо на Bereza RP!\nСтворіть новий пароль для реєстрації (від 4 символів):",
            "Далі", "Вихід");
    }
    return 1;
}

public OnPlayerDisconnect(playerid, reason)
{
    if(PlayerInfo[playerid][pLogged])
    {
        SaveAccount(playerid);
    }
    PlayerInfo[playerid][pLogged] = false;
    return 1;
}

public OnPlayerEnterRaceCheckpoint(playerid)
{
    DisablePlayerRaceCheckpoint(playerid);
    SendClientMessage(playerid, COLOR_GREEN, "[GPS] Ви прибули до призначення!");
    return 1;
}

public OnPlayerRequestClass(playerid, classid)
{
    if(!PlayerInfo[playerid][pLogged])
    {
        new path[64];
        GetPlayerAccountPath(playerid, path, sizeof(path));
        if(fexist(path))
        {
            ShowPlayerDialog(playerid, DIALOG_LOGIN, DIALOG_STYLE_PASSWORD,
                "{00FF00}Авторизація",
                "{FFFFFF}Введіть ваш пароль для входу в гру:",
                "Увійти", "Вихід");
        }
        else
        {
            ShowPlayerDialog(playerid, DIALOG_REGISTER, DIALOG_STYLE_PASSWORD,
                "{00FF00}Реєстрація акаунту",
                "{FFFFFF}Створіть новий пароль для реєстрації (від 4 символів):",
                "Далі", "Вихід");
        }
        return 0;
    }

    SetSpawnInfo(playerid, 0, PlayerInfo[playerid][pSkin], 1759.0186, -1898.1260, 13.5622, 266.4503, 0, 0, 0, 0, 0, 0);
    SpawnPlayer(playerid);
    return 1;
}

public OnPlayerSpawn(playerid)
{
    SetPlayerInterior(playerid, 0);
    SetPlayerVirtualWorld(playerid, 0);
    SetPlayerPos(playerid, 1759.0186, -1898.1260, 13.5622);
    SetPlayerFacingAngle(playerid, 266.4503);
    SetCameraBehindPlayer(playerid);
    TogglePlayerControllable(playerid, true);
    return 1;
}

public OnPlayerKeyStateChange(playerid, newkeys, oldkeys)
{
    if(newkeys & KEY_WALK)
    {
        if(!PlayerInfo[playerid][pLogged]) return 1;

        // Вхід у Мерію
        if(GetPlayerVirtualWorld(playerid) == 0 && IsPlayerInRangeOfPoint(playerid, 2.0, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z))
        {
            SetPlayerInterior(playerid, MAYOR_INT_ID);
            SetPlayerVirtualWorld(playerid, 100);
            SetPlayerPos(playerid, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z);
            SetPlayerFacingAngle(playerid, MAYOR_INT_A);
            SetCameraBehindPlayer(playerid);
            return 1;
        }

        // Вихід з Мерії
        if(GetPlayerVirtualWorld(playerid) == 100 && IsPlayerInRangeOfPoint(playerid, 2.0, MAYOR_INT_X, MAYOR_INT_Y, MAYOR_INT_Z))
        {
            SetPlayerInterior(playerid, 0);
            SetPlayerVirtualWorld(playerid, 0);
            SetPlayerPos(playerid, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z);
            SetPlayerFacingAngle(playerid, MAYOR_ENT_A);
            SetCameraBehindPlayer(playerid);
            return 1;
        }

        // Бот Віталік
        if(IsPlayerInRangeOfPoint(playerid, 2.5, 1808.4893, -1900.0646, 13.6783))
        {
            ShowPlayerDialog(playerid, DIALOG_QUESTS, DIALOG_STYLE_LIST,
                "{FFFF00}Квестова лінія — Віталік",
                "1. Отримання громадянства\n\
                2. Виконати 10 дій (Завод/Ферма/Лісопилка)\n\
                3. Отримання водійських прав\n\
                4. Робота автобусника\n\
                5. Робота таксиста\n\
                6. Перша машина\n\
                7. Перший одяг\n\
                8. Отримання мед карти в лікарні\n\
                9. Права на водний транспорт\n\
                10. Права на управління літаком",
                "Обрати", "Закрити");
            return 1;
        }

        // Оренда мопеда
        if(IsPlayerInRangeOfPoint(playerid, 2.0, 1805.5000, -1900.0646, 13.5622))
        {
            ShowPlayerDialog(playerid, DIALOG_RENT_FAGGIO, DIALOG_STYLE_MSGBOX,
                "{00FF00}Оренда мопеда",
                "{FFFFFF}Ви бажаєте орендувати мопед за {00FF00}250 грн{FFFFFF}?",
                "Орендувати", "Скасувати");
            return 1;
        }

        // Вхід у бізнеси
        if(GetPlayerVirtualWorld(playerid) == 0)
        {
            for(new i = 0; i < TotalBiz; i++)
            {
                if(IsPlayerInRangeOfPoint(playerid, 2.5, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ]) || IsPlayerInRangeOfPoint(playerid, 2.5, BizInfo[i][bEntX], BizInfo[i][bEntY], BizInfo[i][bEntZ]))
                {
                    if(!strcmp(BizInfo[i][bOwner], "The State", true))
                    {
                        SelectedBiz[playerid] = i;
                        
                        new title_str[64], dialog_msg[256];
                        format(title_str, sizeof(title_str), "{FFFF00}%s", BizInfo[i][bName]);
                        format(dialog_msg, sizeof(dialog_msg), "1. Зайти в бізнес\n2. Купити бізнес ({00FF00}%d грн{FFFFFF})", BizInfo[i][bPrice]);

                        ShowPlayerDialog(playerid, DIALOG_BIZ_MENU, DIALOG_STYLE_LIST, title_str, dialog_msg, "Обрати", "Скасувати");
                    }
                    else
                    {
                        EnterBusiness(playerid, i);
                    }
                    return 1;
                }
            }
        }
        else if(GetPlayerVirtualWorld(playerid) != 100) // Якщо гравець не в мерії
        {
            new biz_idx = GetPlayerVirtualWorld(playerid) - 1;
            if(biz_idx >= 0 && biz_idx < TotalBiz)
            {
                new bool:near_exit = false;

                switch(BizInfo[biz_idx][bType])
                {
                    case 0: if(IsPlayerInRangeOfPoint(playerid, 3.0, -25.8884, -55.8605, 1003.5469)) near_exit = true;
                    case 1: if(IsPlayerInRangeOfPoint(playerid, 3.0, 207.7379, -109.0199, 1005.1328)) near_exit = true;
                    case 2: if(IsPlayerInRangeOfPoint(playerid, 3.0, 285.8000, -38.0000, 1001.5156)) near_exit = true;
                    case 3: if(IsPlayerInRangeOfPoint(playerid, 3.0, 365.6203, -11.6033, 1001.8516)) near_exit = true;
                }

                if(near_exit)
                {
                    SetPlayerInterior(playerid, 0);
                    SetPlayerVirtualWorld(playerid, 0);

                    SetPlayerPos(playerid, BizInfo[biz_idx][bPickX], BizInfo[biz_idx][bPickY], BizInfo[biz_idx][bPickZ]);
                    SetPlayerFacingAngle(playerid, BizInfo[biz_idx][bEntA]);
                    SetCameraBehindPlayer(playerid);
                    return 1;
                }
            }
        }
    }
    return 1;
}

// =============================================================================
// 5. ДІАЛОГИ
// =============================================================================
public OnDialogResponse(playerid, dialogid, response, listitem, inputtext[])
{
    switch(dialogid)
    {
        case DIALOG_REGISTER:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Ви скасували реєстрацію.");
                Kick(playerid);
                return 1;
            }

            if(strlen(inputtext) < 4 || strlen(inputtext) > 32)
            {
                ShowPlayerDialog(playerid, DIALOG_REGISTER, DIALOG_STYLE_PASSWORD,
                    "{FF0000}Помилка реєстрації",
                    "{FFFFFF}Пароль має містити від 4 до 32 символів!\nВведіть пароль ще раз:",
                    "Далі", "Вихід");
                return 1;
            }

            format(PlayerInfo[playerid][pPassword], 64, "%s", inputtext);

            ShowPlayerDialog(playerid, DIALOG_SEX, DIALOG_STYLE_LIST,
                "{00FF00}Вибір статі персонажа",
                "1. Чоловіча\n2. Жіноча",
                "Обрати", "Вихід");

            return 1;
        }

        case DIALOG_SEX:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Ви скасували реєстрацію.");
                Kick(playerid);
                return 1;
            }

            PlayerInfo[playerid][pSex] = (listitem == 0) ? 1 : 2;
            PlayerInfo[playerid][pSkinIndex] = 0;

            new skinid = (PlayerInfo[playerid][pSex] == 1) ? MaleSkins[0] : FemaleSkins[0];

            SetSpawnInfo(playerid, 0, skinid, 1759.0186, -1898.1260, 13.5622, 90.0, 0, 0, 0, 0, 0, 0);
            SpawnPlayer(playerid);

            SetPlayerPos(playerid, 1759.0186, -1898.1260, 13.5622);
            SetPlayerFacingAngle(playerid, 90.0);
            SetPlayerSkin(playerid, skinid);
            TogglePlayerControllable(playerid, false);

            SetPlayerCameraPos(playerid, 1755.2, -1898.1260, 14.8);
            SetPlayerCameraLookAt(playerid, 1759.0186, -1898.1260, 14.8);

            ShowSkinSelectionDialog(playerid);
            return 1;
        }

        case DIALOG_SKIN_SELECT:
        {
            if(response)
            {
                new selected_skin = (PlayerInfo[playerid][pSex] == 1) ? MaleSkins[PlayerInfo[playerid][pSkinIndex]] : FemaleSkins[PlayerInfo[playerid][pSkinIndex]];
                
                PlayerInfo[playerid][pSkin] = selected_skin;
                PlayerInfo[playerid][pID] = GetNextAccountID();
                PlayerInfo[playerid][pLogged] = true;

                SaveAccount(playerid);

                TogglePlayerControllable(playerid, true);
                SendClientMessage(playerid, COLOR_GREEN, "Вітаємо! Ваш акаунт успішно створено. Приємної гри!");

                SetSpawnInfo(playerid, 0, selected_skin, 1759.0186, -1898.1260, 13.5622, 266.4503, 0, 0, 0, 0, 0, 0);
                SpawnPlayer(playerid);
            }
            else
            {
                PlayerInfo[playerid][pSkinIndex] = (PlayerInfo[playerid][pSkinIndex] + 1) % 3;

                new current_skin = (PlayerInfo[playerid][pSex] == 1) ? MaleSkins[PlayerInfo[playerid][pSkinIndex]] : FemaleSkins[PlayerInfo[playerid][pSkinIndex]];
                
                SetPlayerSkin(playerid, current_skin);
                ShowSkinSelectionDialog(playerid);
            }
            return 1;
        }

        case DIALOG_LOGIN:
        {
            if(!response)
            {
                SendClientMessage(playerid, COLOR_GREY, "Ви скасували вхід.");
                Kick(playerid);
                return 1;
            }

            if(strlen(inputtext) < 1)
            {
                ShowPlayerDialog(playerid, DIALOG_LOGIN, DIALOG_STYLE_PASSWORD,
                    "{FF0000}Помилка входу",
                    "{FFFFFF}Поле пароля не може бути порожнім!\nВведіть ваш пароль:",
                    "Увійти", "Вихід");
                return 1;
            }

            if(CheckPassword(playerid, inputtext))
            {
                LoadAccount(playerid);
                PlayerInfo[playerid][pLogged] = true;
                SendClientMessage(playerid, COLOR_GREEN, "Ви успішно авторизувалися! Приємної гри.");

                SetSpawnInfo(playerid, 0, PlayerInfo[playerid][pSkin], 1759.0186, -1898.1260, 13.5622, 266.4503, 0, 0, 0, 0, 0, 0);
                SpawnPlayer(playerid);
            }
            else
            {
                ShowPlayerDialog(playerid, DIALOG_LOGIN, DIALOG_STYLE_PASSWORD,
                    "{FF0000}Невірний пароль!",
                    "{FFFFFF}Ви ввели невірний пароль.\nСпробуйте ще раз:",
                    "Увійти", "Вихід");
            }
            return 1;
        }

        case DIALOG_MM:
        {
            if(!response) return 1;

            switch(listitem)
            {
                case 0:
                {
                    new name[MAX_PLAYER_NAME], stats_msg[1024];
                    GetPlayerName(playerid, name, sizeof(name));

                    new hour, minute, second, day, month, year;
                    gettime(hour, minute, second);
                    getdate(year, month, day);

                    new sex_str[16];
                    format(sex_str, sizeof(sex_str), (PlayerInfo[playerid][pSex] == 1) ? "Чоловіча" : "Жіноча");

                    format(stats_msg, sizeof(stats_msg),
                        "{FFFFFF}Ім'я:\t\t\t\t{33AAFF}%s\n\
                        {FFFFFF}Номер акаунту:\t\t{33AAFF}%d\n\
                        {FFFFFF}Адмін-рівень:\t\t{33AAFF}%d lvl\n\
                        {FFFFFF}Рівень:\t\t\t\t{33AAFF}1\n\
                        {FFFFFF}Гроші:\t\t\t\t{00FF00}%d грн\n\
                        {FFFFFF}Очки досвіду:\t\t{33AAFF}0 з 4\n\
                        {FFFFFF}Вік:\t\t\t\t{33AAFF}18\n\
                        {FFFFFF}Стать:\t\t\t\t{33AAFF}%s\n\
                        {FFFFFF}Поточний час:\t\t{33AAFF}%02d:%02d:%02d\n\
                        {FFFFFF}Поточна дата:\t\t{33AAFF}%02d.%02d.%d",
                        name, PlayerInfo[playerid][pID], PlayerInfo[playerid][pAdmin], GetPlayerMoney(playerid), sex_str, hour, minute, second, day, month, year);

                    ShowPlayerDialog(playerid, DIALOG_STATS, DIALOG_STYLE_MSGBOX,
                        "{FFFF00}Статистика персонажа",
                        stats_msg,
                        "Закрити", "");
                }
                case 1: SendClientMessage(playerid, COLOR_YELLOW, "[Підказка] Основні команди: /mm, /gps, /cords, /giveadmin, /makeadmin, /spawnveh, /getcord, /makebiz, /delbiz");
                case 2: SendClientMessage(playerid, COLOR_GREY, "Цей розділ знаходиться у розробці.");
            }
            return 1;
        }

        case DIALOG_BIZ_MENU:
        {
            if(!response) return 1;

            new biz_id = SelectedBiz[playerid];
            if(biz_id < 0 || biz_id >= TotalBiz) return 1;

            if(listitem == 0)
            {
                EnterBusiness(playerid, biz_id);
            }
            else if(listitem == 1)
            {
                if(GetPlayerMoney(playerid) < BizInfo[biz_id][bPrice])
                {
                    return SendClientMessage(playerid, COLOR_RED, "У вас недостатньо коштів для купівлі цього бізнесу!");
                }

                if(strcmp(BizInfo[biz_id][bOwner], "The State", true) != 0)
                {
                    return SendClientMessage(playerid, COLOR_RED, "Цей бізнес вже був кимось куплений!");
                }

                GivePlayerMoney(playerid, -BizInfo[biz_id][bPrice]);

                new name[MAX_PLAYER_NAME];
                GetPlayerName(playerid, name, sizeof(name));
                format(BizInfo[biz_id][bOwner], MAX_PLAYER_NAME, "%s", name);

                SaveBusinesses();
                SaveAccount(playerid);
                UpdateBizText(biz_id);

                new msg[144];
                format(msg, sizeof(msg), "Вітаємо! Ви придбали бізнес '%s' за %d грн!", BizInfo[biz_id][bName], BizInfo[biz_id][bPrice]);
                SendClientMessage(playerid, COLOR_GREEN, msg);
            }
            return 1;
        }

        // --- ДІАЛОГИ GPS ---
        case DIALOG_GPS_MAIN:
        {
            if(!response) return 1;

            if(listitem == 0)
            {
                // Основне
                ShowPlayerDialog(playerid, DIALOG_GPS_MAIN_LOCS, DIALOG_STYLE_LIST,
                    "{FFFF00}GPS — Основні місця",
                    "1. Мерія м. Лос-Сантос",
                    "Обрати", "Назад");
            }
            else if(listitem == 1)
            {
                // Бізнеси
                ShowPlayerDialog(playerid, DIALOG_GPS_BIZ_CATS, DIALOG_STYLE_LIST,
                    "{FFFF00}GPS — Категорії бізнесів",
                    "1. Магазини АТБ",
                    "Обрати", "Назад");
            }
            return 1;
        }

       case DIALOG_GPS_MAIN_LOCS:
        {
            if(!response) return pc_cmd_gps(playerid, ""); // Змінено cmd_gps на pc_cmd_gps

            if(listitem == 0)
            {
                SetGPSDestination(playerid, MAYOR_ENT_X, MAYOR_ENT_Y, MAYOR_ENT_Z, MAYOR_ENT_A, "Мерія м. Лос-Сантос");
            }
            return 1;
        }

        case DIALOG_GPS_BIZ_CATS:
        {
            if(!response) return pc_cmd_gps(playerid, ""); // Змінено cmd_gps на pc_cmd_gps

            if(listitem == 0) // Магазини АТБ
            {
                new list_str[2048] = "", count = 0;
                new Float:px, Float:py, Float:pz;
                GetPlayerPos(playerid, px, py, pz);

                for(new i = 0; i < TotalBiz; i++)
                {
                    if(BizInfo[i][bType] == 0) // Тип 0: АТБ
                    {
                        new Float:dist = GetPlayerDistanceFromPoint(playerid, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ]);
                        new item[128];
                        format(item, sizeof(item), "%d. %s [%.0f м]\n", count + 1, BizInfo[i][bName], dist);
                        strcat(list_str, item);
                        
                        GPS_ATB_Index[playerid][count] = i;
                        count++;
                    }
                }

                if(count == 0)
                {
                    SendClientMessage(playerid, COLOR_RED, "У базі немає доступних магазинів АТБ!");
                    return 1;
                }

                ShowPlayerDialog(playerid, DIALOG_GPS_ATB, DIALOG_STYLE_LIST,
                    "{FFFF00}GPS — Магазини АТБ",
                    list_str,
                    "Обрати", "Назад");
            }
            return 1;
        }

        case DIALOG_GPS_ATB:
        {
            if(!response)
            {
                ShowPlayerDialog(playerid, DIALOG_GPS_BIZ_CATS, DIALOG_STYLE_LIST,
                    "{FFFF00}GPS — Категорії бізнесів",
                    "1. Магазини АТБ",
                    "Обрати", "Назад");
                return 1;
            }

            new biz_idx = GPS_ATB_Index[playerid][listitem];
            SetGPSDestination(playerid, BizInfo[biz_idx][bPickX], BizInfo[biz_idx][bPickY], BizInfo[biz_idx][bPickZ], BizInfo[biz_idx][bEntA], BizInfo[biz_idx][bName]);
            return 1;
        }

        case DIALOG_QUESTS:
        {
            if(!response) return 1;

            SelectedQuest[playerid] = listitem;
            new title[64], desc[512];

            switch(listitem)
            {
                case 0:
                {
                    format(title, sizeof(title), "{FFFF00}1. Отримання громадянства");
                    format(desc, sizeof(desc), "{FFFFFF}Ласкаво просимо! Для повноцінного життя в нашому штаті вам потрібно оформити паспорт та отримати громадянство у Мерії міста.\n\n{00FF00}Винагорода:\n{FFFFFF}- 2,000 грн\n- 2 EXP");
                }
                case 1:
                {
                    format(title, sizeof(title), "{FFFF00}2. 10 дій на початковій роботі");
                    format(desc, sizeof(desc), "{FFFFFF}Покажіть свою працьовитість! Вирушайте на Завод, Ферму або Лісопилку та виконайте 10 робочих дій.\n\n{00FF00}Винагорода:\n{FFFFFF}- 2,500 грн\n- 3 EXP");
                }
                case 2:
                {
                    format(title, sizeof(title), "{FFFF00}3. Отримання водійських прав");
                    format(desc, sizeof(desc), "{FFFFFF}Щоб легально керувати автомобілем, завітайте до Автошколи та успішно складіть іспит із водіння.\n\n{00FF00}Винагорода:\n{FFFFFF}- 1,500 грн\n- 2 EXP");
                }
                case 3:
                {
                    format(title, sizeof(title), "{FFFF00}4. Робота автобусника");
                    format(desc, sizeof(desc), "{FFFFFF}Влаштуйтеся автобусником у Центрі Зайнятості та виконайте свій перший міський рейс.\n\n{00FF00}Винагорода:\n{FFFFFF}- 3,000 грн\n- 3 EXP");
                }
                case 4:
                {
                    format(title, sizeof(title), "{FFFF00}5. Робота таксиста");
                    format(desc, sizeof(desc), "{FFFFFF}Отримайте роботу таксиста, вийдіть на зміну та виконайте перше замовлення клієнта.\n\n{00FF00}Винагорода:\n{FFFFFF}- 3,500 грн\n- 3 EXP");
                }
                case 5:
                {
                    format(title, sizeof(title), "{FFFF00}6. Перша машина");
                    format(desc, sizeof(desc), "{FFFFFF}Придбайте свій перший власний автомобіль в Автосалоні штату.\n\n{00FF00}Винагорода:\n{FFFFFF}- 5,000 грн\n- 5 EXP");
                }
                case 6:
                {
                    format(title, sizeof(title), "{FFFF00}7. Перший одяг");
                    format(desc, sizeof(desc), "{FFFFFF}Час оновити гардероб! Завітайте до будь-якого магазину одягу та придбайте новий костюм.\n\n{00FF00}Винагорода:\n{FFFFFF}- 1,500 грн\n- 2 EXP");
                }
                case 7:
                {
                    format(title, sizeof(title), "{FFFF00}8. Отримання мед карти");
                    format(desc, sizeof(desc), "{FFFFFF}Завітайте до Лікарні, пройдiть медичний огляд у лікаря та отримайте офіційну мед карту.\n\n{00FF00}Винагорода:\n{FFFFFF}- 2,000 грн\n- 2 EXP");
                }
                case 8:
                {
                    format(title, sizeof(title), "{FFFF00}9. Права на водний транспорт");
                    format(desc, sizeof(desc), "{FFFFFF}Пройдіть навчання на човновій станції Автошколи та отримайте ліцензію на водний транспорт.\n\n{00FF00}Винагорода:\n{FFFFFF}- 4,000 грн\n- 4 EXP");
                }
                case 9:
                {
                    format(title, sizeof(title), "{FFFF00}10. Права на управління літаком");
                    format(desc, sizeof(desc), "{FFFFFF}Підкоріть небо! Складіть практичний іспит з пілотування у Авіашколі аеропорту.\n\n{00FF00}Винагорода:\n{FFFFFF}- 6,000 грн\n- 5 EXP");
                }
            }

            ShowPlayerDialog(playerid, DIALOG_QUEST_INFO, DIALOG_STYLE_MSGBOX, title, desc, "Прийняти", "Назад");
            return 1;
        }

        case DIALOG_QUEST_INFO:
        {
            if(!response)
            {
                ShowPlayerDialog(playerid, DIALOG_QUESTS, DIALOG_STYLE_LIST,
                    "{FFFF00}Квестова лінія — Віталік",
                    "1. Отримання громадянства\n\
                    2. Виконати 10 дій (Завод/Ферма/Лісопилка)\n\
                    3. Отримання водійських прав\n\
                    4. Робота автобусника\n\
                    5. Робота таксиста\n\
                    6. Перша машина\n\
                    7. Перший одяг\n\
                    8. Отримання мед карти в лікарні\n\
                    9. Права на водний транспорт\n\
                    10. Права на управління літаком",
                    "Обрати", "Закрити");
                return 1;
            }

            SendClientMessage(playerid, COLOR_GREEN, "[Квест] Ви успішно прийняли завдання! Виконайте його для отримання винагороди.");
            return 1;
        }

        case DIALOG_RENT_FAGGIO:
        {
            if(!response) return 1;

            if(GetPlayerMoney(playerid) < 250) return SendClientMessage(playerid, COLOR_RED, "У вас недостатньо коштів (250 грн)!");

            GivePlayerMoney(playerid, -250);

            new Float:x, Float:y, Float:z, Float:a;
            GetPlayerPos(playerid, x, y, z);
            GetPlayerFacingAngle(playerid, a);

            new veh = CreateVehicle(462, x + 1.5, y + 1.5, z, a, 1, 1, -1);
            PutPlayerInVehicle(playerid, veh, 0);

            SendClientMessage(playerid, COLOR_GREEN, "[Оренда] Ви успішно орендували мопед Faggio за 250 грн!");
            return 1;
        }

        case DIALOG_MAKEBIZ:
        {
            if(!response) return 1;

            if(TotalBiz >= MAX_BIZ)
            {
                return SendClientMessage(playerid, COLOR_RED, "Досягнуто ліміт бізнесів на сервері!");
            }

            new Float:x, Float:y, Float:z, Float:a;
            GetPlayerPos(playerid, x, y, z);
            GetPlayerFacingAngle(playerid, a);

            new i = TotalBiz;
            BizInfo[i][bID] = i + 1;
            BizInfo[i][bType] = listitem;
            BizInfo[i][bPrice] = TempBizPrice[playerid];
            format(BizInfo[i][bOwner], MAX_PLAYER_NAME, "The State");
            BizInfo[i][bEntX] = x;
            BizInfo[i][bEntY] = y;
            BizInfo[i][bEntZ] = z;
            BizInfo[i][bEntA] = a;

            switch(listitem)
            {
                case 0: format(BizInfo[i][bName], 64, "АТБ");
                case 1: format(BizInfo[i][bName], 64, "Магазин одягу");
                case 2: format(BizInfo[i][bName], 64, "Магазин зброї");
                case 3: format(BizInfo[i][bName], 64, "Макдональдс");
            }

            new Float:pX, Float:pY;
            GetOffsetPos(x, y, a, 1.2, pX, pY);

            BizInfo[i][bPickX] = pX;
            BizInfo[i][bPickY] = pY;
            BizInfo[i][bPickZ] = z;

            BizInfo[i][bPickupID] = CreateDynamicPickup(1318, 23, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ]);

            new label_str[256];
            format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {FF0000}Продається\n{FFFF00}Вартість: {00FF00}%d грн\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для меню",
                BizInfo[i][bID], BizInfo[i][bName], BizInfo[i][bPrice]);

            BizInfo[i][bTextID] = CreateDynamic3DTextLabel(label_str, COLOR_WHITE, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ] + 0.5, 10.0);

            TotalBiz++;
            SaveBusinesses();

            new msg[144];
            format(msg, sizeof(msg), "[Адмін] Ви успішно створили бізнес '%s' (ID: %d) за %d грн!", BizInfo[i][bName], BizInfo[i][bID], TempBizPrice[playerid]);
            SendClientMessage(playerid, COLOR_GREEN, msg);
            return 1;
        }
    }
    return 1;
}

// =============================================================================
// 6. КОМАНДИ
// =============================================================================

cmd:mm(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");

    ShowPlayerDialog(playerid, DIALOG_MM, DIALOG_STYLE_LIST,
        "{00FF00}Головне меню — Bereza RP",
        "1. Статистика персонажа\n2. Команди сервера\n3. Налаштування акаунту",
        "Обрати", "Закрити");

    return 1;
}

cmd:gps(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");

    ShowPlayerDialog(playerid, DIALOG_GPS_MAIN, DIALOG_STYLE_LIST,
        "{FFFF00}Навігатор — Bereza RP",
        "1. Основні місця\n2. Бізнеси",
        "Обрати", "Закрити");

    return 1;
}

cmd:getcord(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");
    if(PlayerInfo[playerid][pAdmin] < 1) return SendClientMessage(playerid, COLOR_RED, "Ви не адмін!");

    new target_id;
    if(sscanf(params, "d", target_id)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /getcord [ID бізнесу]");

    new biz_idx = -1;
    for(new i = 0; i < TotalBiz; i++)
    {
        if(BizInfo[i][bID] == target_id)
        {
            biz_idx = i;
            break;
        }
    }

    if(biz_idx == -1) return SendClientMessage(playerid, COLOR_RED, "Бізнес із таким ID не знайдено!");

    new Float:x, Float:y, Float:z;
    GetPlayerPos(playerid, x, y, z);

    BizInfo[biz_idx][bPickX] = x;
    BizInfo[biz_idx][bPickY] = y;
    BizInfo[biz_idx][bPickZ] = z;

    DestroyDynamicPickup(BizInfo[biz_idx][bPickupID]);
    DestroyDynamic3DTextLabel(BizInfo[biz_idx][bTextID]);

    BizInfo[biz_idx][bPickupID] = CreateDynamicPickup(1318, 23, x, y, z);

    new label_str[256];
    if(!strcmp(BizInfo[biz_idx][bOwner], "The State", true))
    {
        format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {FF0000}Продається\n{FFFF00}Вартість: {00FF00}%d грн\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для меню",
            BizInfo[biz_idx][bID], BizInfo[biz_idx][bName], BizInfo[biz_idx][bPrice]);
    }
    else
    {
        format(label_str, sizeof(label_str), "{FFFF00}Бізнес [%d]: {FFFFFF}%s\n{FFFF00}Власник: {00FF00}%s\n{FFFFFF}Натисніть {00FF00}ALT{FFFFFF} для входу",
            BizInfo[biz_idx][bID], BizInfo[biz_idx][bName], BizInfo[biz_idx][bOwner]);
    }

    BizInfo[biz_idx][bTextID] = CreateDynamic3DTextLabel(label_str, COLOR_WHITE, x, y, z + 0.5, 10.0);

    SaveBusinesses();

    new msg[144];
    format(msg, sizeof(msg), "[Адмін] Ви перенесли мітку бізнесу '%s' (ID: %d) на свої координати (X: %.2f | Y: %.2f | Z: %.2f)!", BizInfo[biz_idx][bName], target_id, x, y, z);
    SendClientMessage(playerid, COLOR_GREEN, msg);

    return 1;
}

cmd:spawnveh(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");
    if(PlayerInfo[playerid][pAdmin] < 1) return SendClientMessage(playerid, COLOR_RED, "Ви не адмін!");

    new veh_id, col1, col2;
    if(sscanf(params, "dI(-1)I(-1)", veh_id, col1, col2)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /spawnveh [ID авто 400-611] [колір 1] [колір 2]");

    if(veh_id < 400 || veh_id > 611) return SendClientMessage(playerid, COLOR_RED, "ID транспортного засобу має бути від 400 до 611!");

    new Float:x, Float:y, Float:z, Float:a;
    GetPlayerPos(playerid, x, y, z);
    GetPlayerFacingAngle(playerid, a);

    new created_veh = CreateVehicle(veh_id, x + 1.0, y + 1.0, z, a, col1, col2, -1);
    PutPlayerInVehicle(playerid, created_veh, 0);

    new msg[144];
    format(msg, sizeof(msg), "[Адмін] Ви створили транспортний засіб ID: %d!", veh_id);
    SendClientMessage(playerid, COLOR_GREEN, msg);
    return 1;
}

cmd:makeadmin(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");
    if(PlayerInfo[playerid][pAdmin] < 8) return SendClientMessage(playerid, COLOR_RED, "Ця команда доступна лише адмінам 8+ рівня!");

    new targetid, level;
    if(sscanf(params, "ud", targetid, level)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /makeadmin [id гравця] [рівень 1-9]");

    if(!IsPlayerConnected(targetid) || !PlayerInfo[targetid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Гравець не в мережі або не авторизований!");
    if(level < 0 || level > 9) return SendClientMessage(playerid, COLOR_RED, "Рівень адмін-прав має бути від 0 до 9! (10 рівень дається лише через /giveadmin)");

    PlayerInfo[targetid][pAdmin] = level;
    SaveAccount(targetid);

    new sender_name[MAX_PLAYER_NAME], target_name[MAX_PLAYER_NAME], msg[144];
    GetPlayerName(playerid, sender_name, sizeof(sender_name));
    GetPlayerName(targetid, target_name, sizeof(target_name));

    format(msg, sizeof(msg), "[Адмін] Адміністратор %s надав вам %d рівень адмін-прав!", sender_name, level);
    SendClientMessage(targetid, COLOR_GREEN, msg);

    format(msg, sizeof(msg), "[Адмін] Ви надали %d рівень адмін-прав гравцю %s!", level, target_name);
    SendClientMessage(playerid, COLOR_GREEN, msg);

    return 1;
}

cmd:cords(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");

    new Float:x, Float:y, Float:z, Float:a;
    GetPlayerPos(playerid, x, y, z);
    GetPlayerFacingAngle(playerid, a);

    new msg[144];
    format(msg, sizeof(msg), "[Координати] X: %.4f | Y: %.4f | Z: %.4f | Кут: %.4f", x, y, z, a);
    SendClientMessage(playerid, COLOR_YELLOW, msg);

    return 1;
}

cmd:giveadmin(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");

    if(PlayerInfo[playerid][pID] != 100001)
    {
        return SendClientMessage(playerid, COLOR_RED, "Помилка: Ви не є першим зареєстрованим гравцем сервера (№1)!");
    }

    PlayerInfo[playerid][pAdmin] = 10;
    SaveAccount(playerid);

    SendClientMessage(playerid, COLOR_GREEN, "[Адмін] Вам успішно надано 10 рівень адмін-прав!");
    return 1;
}

cmd:givemoney(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return 1;
    if(PlayerInfo[playerid][pAdmin] < 10) return SendClientMessage(playerid, COLOR_RED, "Недостатньо прав!");

    new targetid, amount;
    if(sscanf(params, "ui", targetid, amount)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /givemoney [id] [сума]");
    if(!IsPlayerConnected(targetid)) return SendClientMessage(playerid, COLOR_RED, "Гравець не в мережі!");

    GivePlayerMoney(targetid, amount);
    SaveAccount(targetid);

    new str[144];
    format(str, sizeof(str), "[Адмін] Ви видали %d грн гравцю ID %d.", amount, targetid);
    SendClientMessage(playerid, COLOR_GREEN, str);
    return 1;
}

cmd:a(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return 1;
    if(PlayerInfo[playerid][pAdmin] < 1) return SendClientMessage(playerid, COLOR_RED, "Ви не адмін!");

    if(isnull(params)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /a [текст]");

    new name[MAX_PLAYER_NAME], str[144];
    GetPlayerName(playerid, name, sizeof(name));
    format(str, sizeof(str), "[Admin Chat] %s [%d lvl]: %s", name, PlayerInfo[playerid][pAdmin], params);

    for(new i = 0; i < MAX_PLAYERS; i++)
    {
        if(IsPlayerConnected(i) && PlayerInfo[i][pLogged] && PlayerInfo[i][pAdmin] > 0)
        {
            SendClientMessage(i, COLOR_YELLOW, str);
        }
    }
    return 1;
}

cmd:makebiz(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");
    if(PlayerInfo[playerid][pAdmin] < 1) return SendClientMessage(playerid, COLOR_RED, "Ви не адмін!");

    new price;
    if(sscanf(params, "d", price)) return SendClientMessage(playerid, COLOR_GREY, "Використання: /makebiz [ціна]");
    if(price < 1) return SendClientMessage(playerid, COLOR_RED, "Ціна має бути більше 0 грн!");

    TempBizPrice[playerid] = price;

    ShowPlayerDialog(playerid, DIALOG_MAKEBIZ, DIALOG_STYLE_LIST,
        "{00FF00}Створення бізнесу — Оберіть тип",
        "1. АТБ\n2. Магазин одягу\n3. Магазин зброї\n4. Макдональдс",
        "Створити", "Скасувати");

    return 1;
}

cmd:delbiz(playerid, params[])
{
    if(!PlayerInfo[playerid][pLogged]) return SendClientMessage(playerid, COLOR_RED, "Ви повинні авторизуватися!");
    if(PlayerInfo[playerid][pAdmin] < 1) return SendClientMessage(playerid, COLOR_RED, "Ви не адмін!");

    new biz_idx = -1;

    if(!isnull(params))
    {
        new target_id = strval(params);
        for(new i = 0; i < TotalBiz; i++)
        {
            if(BizInfo[i][bID] == target_id)
            {
                biz_idx = i;
                break;
            }
        }
        if(biz_idx == -1) return SendClientMessage(playerid, COLOR_RED, "Бізнес не знайдено!");
    }
    else
    {
        for(new i = 0; i < TotalBiz; i++)
        {
            if(IsPlayerInRangeOfPoint(playerid, 3.0, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ]) || IsPlayerInRangeOfPoint(playerid, 3.0, BizInfo[i][bEntX], BizInfo[i][bEntY], BizInfo[i][bEntZ]))
            {
                biz_idx = i;
                break;
            }
        }
        if(biz_idx == -1) return SendClientMessage(playerid, COLOR_GREY, "Підійдіть до бізнесу або введіть /delbiz [ID]");
    }

    DestroyDynamicPickup(BizInfo[biz_idx][bPickupID]);
    DestroyDynamic3DTextLabel(BizInfo[biz_idx][bTextID]);

    new deleted_name[64], deleted_id;
    format(deleted_name, sizeof(deleted_name), "%s", BizInfo[biz_idx][bName]);
    deleted_id = BizInfo[biz_idx][bID];

    for(new i = biz_idx; i < TotalBiz - 1; i++)
    {
        BizInfo[i][bID] = i + 1;
        format(BizInfo[i][bName], 64, "%s", BizInfo[i+1][bName]);
        format(BizInfo[i][bOwner], MAX_PLAYER_NAME, "%s", BizInfo[i+1][bOwner]);
        BizInfo[i][bType] = BizInfo[i+1][bType];
        BizInfo[i][bPrice] = BizInfo[i+1][bPrice];
        BizInfo[i][bEntX] = BizInfo[i+1][bEntX];
        BizInfo[i][bEntY] = BizInfo[i+1][bEntY];
        BizInfo[i][bEntZ] = BizInfo[i+1][bEntZ];
        BizInfo[i][bEntA] = BizInfo[i+1][bEntA];
        BizInfo[i][bPickX] = BizInfo[i+1][bPickX];
        BizInfo[i][bPickY] = BizInfo[i+1][bPickY];
        BizInfo[i][bPickZ] = BizInfo[i+1][bPickZ];
        BizInfo[i][bPickupID] = BizInfo[i+1][bPickupID];
        BizInfo[i][bTextID] = BizInfo[i+1][bTextID];

        UpdateBizText(i);
    }

    TotalBiz--;
    SaveBusinesses();

    new msg[144];
    format(msg, sizeof(msg), "[Адмін] Ви успішно видалили бізнес '%s' (ID: %d)!", deleted_name, deleted_id);
    SendClientMessage(playerid, COLOR_GREEN, msg);

    return 1;
}

stock LoadBusinessesFromDB()
{
    new Cache:result = mysql_query(dbHandle, "SELECT * FROM `bizz`");
    new rows = cache_num_rows();

    TotalBiz = rows;

    for(new i = 0; i < rows; i++)
    {
        cache_get_value_name(i, "name", BizInfo[i][bName], 64);
        cache_get_value_name(i, "owner", BizInfo[i][bOwner], 64);
        cache_get_value_name_int(i, "type", BizInfo[i][bType]);
        cache_get_value_name_int(i, "price", BizInfo[i][bPrice]);
        cache_get_value_name_float(i, "pick_x", BizInfo[i][bPickX]);
        cache_get_value_name_float(i, "pick_y", BizInfo[i][bPickY]);
        cache_get_value_name_float(i, "pick_z", BizInfo[i][bPickZ]);

        // Створення пікапу входу (19134 - іконка інформації/входу, 23 - тип пікапу)
        CreatePickup(19134, 23, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ], 0);

        // Створення 3D тексту над пікапом
        new text_str[256];
        format(text_str, sizeof(text_str), "{FFFF00}[ %s ]\n{FFFFFF}Власник: {FFFF00}%s\n{FFFFFF}Ціна: {00FF00}$%d", 
            BizInfo[i][bName], BizInfo[i][bOwner], BizInfo[i][bPrice]);
            
        Create3DTextLabel(text_str, 0xFFFFFFFF, BizInfo[i][bPickX], BizInfo[i][bPickY], BizInfo[i][bPickZ] + 0.5, 15.0, 0, 1);
    }
    
    cache_delete(result);
    printf("[MySQL] Завантажено бізнесів: %d", TotalBiz);
    return 1;
}
