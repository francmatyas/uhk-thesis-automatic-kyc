package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.dto.*;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DisplayMode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.NumberFormat;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.DateFormat;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserPreferences;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {
    private final UserPreferencesRepository userPreferencesRepository;

    @Transactional(readOnly = true)
    public GetUserPreferencesDTO getUserPreferences(UUID userId) {
        UserPreferences preferences = userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));
        return GetUserPreferencesDTO.builder()
                .userPreferences(toDTO(preferences))
                .preferencesOptions(getPreferencesOptions())
                .build();
    }

    @Transactional
    public UserPreferencesDTO updateUserPreferences(UUID userId, UpdateUserPreferencesRequest request) {
        UserPreferences preferences = userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        preferences.setLanguage(request.getLanguage());
        preferences.setDisplayMode(request.getDisplayMode());
        preferences.setCurrency(request.getCurrency());
        preferences.setDateFormat(request.getDateFormat());
        preferences.setNumberFormat(request.getNumberFormat());
        preferences.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        preferences.setUse24HourTime(request.getUse24HourTime());

        UserPreferences saved = userPreferencesRepository.save(preferences);
        return toDTO(saved);
    }

    @Transactional
    public UserPreferences createDefaultPreferences(UUID userId) {
        // Toto by mělo být voláno pouze interně - entita User má být načtená z kontextu
        UserPreferences preferences = UserPreferences.builder()
                .build();
        return preferences;
    }

    @Transactional
    public UserPreferences getOrCreatePreferences(User user) {
        return userPreferencesRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserPreferences preferences = UserPreferences.builder()
                            .user(user)
                            .build();
                    return userPreferencesRepository.save(preferences);
                });
    }

    private UserPreferencesDTO toDTO(UserPreferences preferences) {
        return UserPreferencesDTO.builder()
                .id(preferences.getId().toString())
                .language(preferences.getLanguage())
                .displayMode(preferences.getDisplayMode())
                .currency(preferences.getCurrency())
                .dateFormat(preferences.getDateFormat())
                .numberFormat(preferences.getNumberFormat())
                .timezone(preferences.getTimezone())
                .use24HourTime(preferences.isUse24HourTime())
                .build();
    }

    public PreferencesOptionsDTO getPreferencesOptions() {
        return PreferencesOptionsDTO.builder()
                .languages(getAvailableLanguages())
                .currencies(getAvailableCurrencies())
                .timezones(getAvailableTimezones())
                .displayModes(DisplayMode.getOptions())
                .dateFormats(DateFormat.getOptions())
                .numberFormats(NumberFormat.getOptions())
                .build();
    }

    private List<LanguageOptionDTO> getAvailableLanguages() {
        return Arrays.asList(
                LanguageOptionDTO.builder().code("en").label("English").flagUnicode("GB").build(),
                LanguageOptionDTO.builder().code("cs").label("Czech").flagUnicode("CZ").build(),
                LanguageOptionDTO.builder().code("de").label("German").flagUnicode("DE").build(),
                LanguageOptionDTO.builder().code("es").label("Spanish").flagUnicode("ES").build(),
                LanguageOptionDTO.builder().code("fr").label("French").flagUnicode("FR").build(),
                LanguageOptionDTO.builder().code("it").label("Italian").flagUnicode("IT").build(),
                LanguageOptionDTO.builder().code("pl").label("Polish").flagUnicode("PL").build(),
                LanguageOptionDTO.builder().code("pt").label("Portuguese").flagUnicode("PT").build(),
                LanguageOptionDTO.builder().code("ru").label("Russian").flagUnicode("RU").build(),
                LanguageOptionDTO.builder().code("zh").label("Chinese").flagUnicode("CN").build(),
                LanguageOptionDTO.builder().code("ja").label("Japanese").flagUnicode("JP").build(),
                LanguageOptionDTO.builder().code("ko").label("Korean").flagUnicode("KR").build()
        );
    }

    private List<CurrencyOptionDTO> getAvailableCurrencies() {
        return Arrays.asList(
                CurrencyOptionDTO.builder().code("USD").label("US Dollar").symbol("$").flagUnicode("US").build(),
                CurrencyOptionDTO.builder().code("EUR").label("Euro").symbol("€").flagUnicode("EU").build(),
                CurrencyOptionDTO.builder().code("CZK").label("Czech Koruna").symbol("Kč").flagUnicode("CZ").build(),
                CurrencyOptionDTO.builder().code("GBP").label("British Pound").symbol("£").flagUnicode("GB").build(),
                CurrencyOptionDTO.builder().code("JPY").label("Japanese Yen").symbol("¥").flagUnicode("JP").build(),
                CurrencyOptionDTO.builder().code("CNY").label("Chinese Yuan").symbol("¥").flagUnicode("CN").build(),
                CurrencyOptionDTO.builder().code("CHF").label("Swiss Franc").symbol("CHF").flagUnicode("CH").build(),
                CurrencyOptionDTO.builder().code("CAD").label("Canadian Dollar").symbol("C$").flagUnicode("CA").build(),
                CurrencyOptionDTO.builder().code("AUD").label("Australian Dollar").symbol("A$").flagUnicode("AU").build(),
                CurrencyOptionDTO.builder().code("PLN").label("Polish Zloty").symbol("zł").flagUnicode("PL").build(),
                CurrencyOptionDTO.builder().code("RUB").label("Russian Ruble").symbol("₽").flagUnicode("RU").build(),
                CurrencyOptionDTO.builder().code("BRL").label("Brazilian Real").symbol("R$").flagUnicode("BR").build(),
                CurrencyOptionDTO.builder().code("INR").label("Indian Rupee").symbol("₹").flagUnicode("IN").build(),
                CurrencyOptionDTO.builder().code("KRW").label("South Korean Won").symbol("₩").flagUnicode("KR").build()
        );
    }

    private List<TimezoneOptionDTO> getAvailableTimezones() {
        ZonedDateTime now = ZonedDateTime.now();

        // Běžné časové zóny s aktuálními offsety
        List<String> commonTimezones = Arrays.asList(
                "UTC",
                "Europe/London",
                "Europe/Paris",
                "Europe/Berlin",
                "Europe/Prague",
                "Europe/Warsaw",
                "Europe/Rome",
                "Europe/Madrid",
                "Europe/Moscow",
                "America/New_York",
                "America/Chicago",
                "America/Denver",
                "America/Los_Angeles",
                "America/Toronto",
                "America/Sao_Paulo",
                "America/Mexico_City",
                "Asia/Tokyo",
                "Asia/Shanghai",
                "Asia/Hong_Kong",
                "Asia/Singapore",
                "Asia/Dubai",
                "Asia/Kolkata",
                "Australia/Sydney",
                "Australia/Melbourne",
                "Pacific/Auckland"
        );

        return commonTimezones.stream()
                .map(tzId -> {
                    ZoneId zoneId = ZoneId.of(tzId);
                    ZonedDateTime zdt = now.withZoneSameInstant(zoneId);
                    String offset = zdt.getOffset().toString();
                    String displayName = zoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH);

                    return TimezoneOptionDTO.builder()
                            .value(tzId)
                            .label(displayName + " (" + tzId + ")")
                            .offset(offset)
                            .build();
                })
                .sorted(Comparator.comparing(TimezoneOptionDTO::getOffset)
                        .thenComparing(TimezoneOptionDTO::getValue))
                .collect(Collectors.toList());
    }
}
