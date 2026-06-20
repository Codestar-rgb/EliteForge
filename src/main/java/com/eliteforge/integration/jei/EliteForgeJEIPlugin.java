package com.eliteforge.integration.jei;

import com.eliteforge.EliteForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * EliteForgeJEIPlugin — JEI (Just Enough Items) integration stub.
 * <p>
 * <b>Status:</b> STUB — ready for JEI API integration.
 * <p>
 * <b>Background:</b> JEI is the standard recipe viewer for Minecraft. Integrating
 * EliteForge's forging recipes (tempered material upgrades, element merging)
 * into JEI allows players to discover recipes in-game. This is listed as a
 * "medium priority" TODO in HANDOVER.md §18.
 * <p>
 * <b>How to enable full integration:</b>
 * <ol>
 *   <li>Add JEI API dependency to {@code build.gradle} (uncomment the existing
 *       JEI lines in the dependencies block):
 *       <pre>{@code
 *       compileOnly fg.deobf("mezz.jei:jei-1.20.1-common-api:15.2.0.27")
 *       compileOnly fg.deobf("mezz.jei:jei-1.20.1-forge-api:15.2.0.27")
 *       runtimeOnly fg.deobf("mezz.jei:jei-1.20.1-forge:15.2.0.27")
 *       }</pre></li>
 *   <li>Uncomment the {@code @JeiPlugin} annotation below and implement
 *       {@code mezz.jei.api.IModPlugin}.</li>
 *   <li>Register recipe categories for:
 *       <ul>
 *           <li><b>Forging Anvil enhancement</b> — equipment + tempered material → enhanced equipment</li>
 *           <li><b>Tempering Station upgrade</b> — 3× same quality → 1 higher quality</li>
 *           <li><b>Tempering Station element add</b> — material + lapis → material with element</li>
 *           <li><b>Tempering Station element merge</b> — 2× material + gold → merged material</li>
 *       </ul></li>
 * </ol>
 * <p>
 * <b>Why not hard-require JEI:</b> JEI is a client-side mod that not all players
 * install. By keeping JEI integration as a soft-dependency (compileOnly API +
 * runtime optional), EliteForge works with or without JEI.
 * <p>
 * <b>Current behavior:</b> This class is a non-instantiated documentation stub.
 * It does not implement {@code IModPlugin} and is never loaded by JEI. It
 * exists solely to document the integration plan and provide a starting point
 * for future development.
 */
// @mezz.jei.api.JeiPlugin  ← Uncomment when JEI API dependency is added to build.gradle
public class EliteForgeJEIPlugin {

    private static final Logger LOGGER = LogManager.getLogger();

    private EliteForgeJEIPlugin() {
        // Documentation stub — no instantiation
    }

    public static void logStubStatus() {
        LOGGER.debug("EliteForge JEI integration is in stub mode. "
                + "See EliteForgeJEIPlugin class documentation for integration instructions.");
    }
}
