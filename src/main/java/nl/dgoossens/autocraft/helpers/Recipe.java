package nl.dgoossens.autocraft.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.Expose;
import nl.dgoossens.autocraft.AutomatedCrafting;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.*;

public class Recipe {
    private String type = ""; //crafting_shaped, crafting_shapeless or we don't care
    private JsonItem result;
    private transient ItemStack itemResult;

    //Shaped Recipes
    private String[] pattern;
    private Map<Character, JsonElement> key; //JsonElement is either array of JsonItem or JsonItem
    private transient Map<Character, List<ItemStack>> itemKey;

    //Shapeless Recipes
    private Set<JsonElement> ingredients;
    private transient List<ItemStack> itemIngredients;

    //A few NMS classes we use because 1.12 is outdated and doesn't support cool recipes yet.
    private static final Class<?> recipeChoice = getClass("org.bukkit.inventory.RecipeChoice").orElse(null);
    private static final Class<?> exactChoice = recipeChoice == null ? null : recipeChoice.getDeclaredClasses()[0];
    private static final Class<?> materialChoice = recipeChoice == null ? null : recipeChoice.getDeclaredClasses()[1];

    //Get a class and put it in an optional.
    private static Optional<Class<?>> getClass(String className) {
        try {
            return Optional.ofNullable(Class.forName(className));
        } catch (Exception x) {
            return Optional.empty();
        }
    }

    public Recipe() {
    } //Needed for GSON, probably.

    public Recipe(ItemStack result, String[] pattern, Map<Character, List<ItemStack>> key) {
        type = "crafting_shaped";
        this.itemResult = result;
        this.pattern = pattern;
        this.itemKey = key;
    }

    public Recipe(ItemStack result, List<ItemStack> ingredients) {
        type = "crafting_shapeless";
        this.itemResult = result;
        this.itemIngredients = ingredients;
    }

    public Recipe(org.bukkit.inventory.Recipe bukkitRecipe) {
        itemResult = bukkitRecipe.getResult();
        if (bukkitRecipe instanceof ShapedRecipe) {
            type = "crafting_shaped";
            pattern = ((ShapedRecipe) bukkitRecipe).getShape();
            //This system of using spigot's choicemap system doesn't work at the moment. It's the backup system anyways.
            if (MinecraftVersion.get().atLeast(MinecraftVersion.THIRTEEN) && exactChoice != null) {
                try {
                    itemKey = new HashMap<>();
                    //This uses a Draft API so this is the backup system! We prefer loading it ourselves.
                    Map<Character, Object> choiceMap = (Map<Character, Object>) ShapedRecipe.class.getMethod("getChoiceMap").invoke(bukkitRecipe);
                    choiceMap.forEach((k, v) -> {
                        List<ItemStack> values = new ArrayList<>();
                        if (v != null) { //V can be null for some reason.
                            if (exactChoice.isAssignableFrom(v.getClass()) || materialChoice.isAssignableFrom(v.getClass())) {
                                try {
                                    List<Object> choices = (List<Object>) v.getClass().getMethod("getChoices").invoke(v);
                                    for (Object o : choices) {
                                        if (o instanceof Material) values.add(new ItemStack((Material) o));
                                        else values.add((ItemStack) o);
                                    }
                                } catch (Exception x) {
                                    x.printStackTrace();
                                }
                            } else {
                                ItemStack val = null;
                                try {
                                    val = (ItemStack) recipeChoice.getMethod("getItemStack").invoke(v);
                                } catch (Exception x) {
                                    x.printStackTrace();
                                }
                                if (val != null) values.add(val);
                            }
                        }
                        itemKey.put(k, values);
                    });
                    return;
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
            itemKey = new HashMap<>();
            ((ShapedRecipe) bukkitRecipe).getIngredientMap().forEach((k, v) -> {
                if (v == null) return;
                itemKey.put(k, Arrays.asList(v));
            });
        } else if (bukkitRecipe instanceof ShapelessRecipe) {
            type = "crafting_shapeless";
            itemIngredients = new ArrayList<>(((ShapelessRecipe) bukkitRecipe).getIngredientList());
            //((ShapelessRecipe) bukkitRecipe).getIngredientList().forEach(in -> ingredients.add(AutomatedCrafting.GSON.toJsonTree(new JsonItem(in))));
        }
    }

    public String getType() {
        return type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
    }

    public ItemStack getResult() {
        return itemResult != null ? itemResult : result.getStack();
    }

    public String[] getPattern() {
        return pattern;
    }

    public Map<Character, List<ItemStack>> getKeys() {
        if (itemKey != null) return itemKey;
        Map<Character, List<ItemStack>> ret = new HashMap<>();
        key.keySet().forEach(c -> {
            List<ItemStack> val = new ArrayList<>();
            JsonElement i = key.get(c);
            if (i.isJsonArray())
                ((JsonArray) i).forEach(e -> val.add(AutomatedCrafting.GSON.fromJson(e, JsonItem.class).getStack()));
            else
                val.add(AutomatedCrafting.GSON.fromJson(i, JsonItem.class).getStack());
            ret.put(c, val);
        });
        return ret;
    }

    public List<ItemStack> getIngredients() {
        if (itemIngredients != null) return itemIngredients;
        List<ItemStack> ret = new ArrayList<>();
        ingredients.forEach(i -> {
            if (i.isJsonArray())
                ((JsonArray) i).forEach(e -> ret.add(AutomatedCrafting.GSON.fromJson(e, JsonItem.class).getStack()));
            else
                ret.add(AutomatedCrafting.GSON.fromJson(i, JsonItem.class).getStack());
        });
        return ret;
    }

    @Override
    public String toString() {
        return AutomatedCrafting.GSON.toJson(this);
    }
}
