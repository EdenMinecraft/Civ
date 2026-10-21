package isaac.bastion;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link BastionType#getByMaterial}, the material-only lookup the chunk reconciler relies on
 * to recover a placed block's intended type. Types are injected into the static registry by
 * reflection, since loading them from config needs a running server.
 */
class BastionTypeTest {

    private BastionType spongeType;
    private BastionType cityType;
    private BastionType claimType;

    @BeforeEach
    void seedTypes() throws Exception {
        spongeType = typeMock(Material.SPONGE);
        cityType = typeMock(Material.NETHER_WART_BLOCK);
        claimType = typeMock(Material.BONE_BLOCK);

        Map<String, BastionType> types = typesMap();
        types.clear();
        types.put("citybastion", cityType);
        types.put("claimbastion", claimType);
        types.put("bastion", spongeType);
    }

    @AfterEach
    void clearTypes() throws Exception {
        typesMap().clear();
    }

    private static BastionType typeMock(Material material) {
        BastionType type = mock(BastionType.class);
        lenient().when(type.getMaterial()).thenReturn(material);
        return type;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BastionType> typesMap() throws Exception {
        Field field = BastionType.class.getDeclaredField("types");
        field.setAccessible(true);
        return (Map<String, BastionType>) field.get(null);
    }

    @Test
    void getByMaterial_resolvesEachConfiguredMaterial() {
        assertSame(spongeType, BastionType.getByMaterial(Material.SPONGE));
        assertSame(cityType, BastionType.getByMaterial(Material.NETHER_WART_BLOCK));
        assertSame(claimType, BastionType.getByMaterial(Material.BONE_BLOCK));
    }

    @Test
    void getByMaterial_returnsNullForNonBastionMaterialOrNull() {
        assertNull(BastionType.getByMaterial(Material.STONE), "non-bastion material has no type");
        assertNull(BastionType.getByMaterial(null));
    }
}
