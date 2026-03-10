package imaging.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import raytracer.Scene;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;


import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
import static org.lwjgl.vulkan.VK12.vkGetPhysicalDeviceFeatures2;

/**
 * Vulkan compute ray tracer (BVH-aware) using LWJGL.
 */
public class VulkanRayTracer implements AutoCloseable {

    private final Scene scene;
    private final VulkanSceneData data;

    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private int queueFamilyIndex;
    private VkQueue queue;
    private long commandPool;
    private boolean closed;

    private static final int LOCAL_SIZE_X = 8;
    private static final int LOCAL_SIZE_Y = 8;

    public VulkanRayTracer(Scene scene) {
        this.scene = scene;
        this.data = new VulkanSceneData(scene);
        init();
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.lwjgl.vulkan.VK10");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void init() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createInstance(stack);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            pickPhysicalDevice(stack);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createDeviceAndQueue(stack);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createCommandPool(stack);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (device != null) {
            vkDestroyCommandPool(device, commandPool, null);
            vkDestroyDevice(device, null);
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
        }
    }

    public BufferedImage render() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int w = data.width;
            int h = data.height;

            // Buffers
            VkBufferView materials = createFloatBuffer(stack, data.materials);
            VkBufferView spheres = createFloatBuffer(stack, data.spheres);
            VkBufferView triangles = createFloatBuffer(stack, data.triangles);
            VkBufferView planes = createFloatBuffer(stack, data.planes);
            VkBufferView dirLights = createFloatBuffer(stack, data.directionalLights);
            VkBufferView pointLights = createFloatBuffer(stack, data.pointLights);
            VkBufferView bvhMin = createFloatBuffer(stack, data.bvhMinBounds);
            VkBufferView bvhMax = createFloatBuffer(stack, data.bvhMaxBounds);
            VkBufferView bvhMeta = createIntBuffer(stack, data.bvhMeta);

            ByteBuffer paramsBuf = packSceneParams(0, 0, w, h);
            VkBufferView sceneParams = createStorageBuffer(stack, paramsBuf.remaining(), paramsBuf,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            MemoryUtil.memFree(paramsBuf);

            VkBufferView output = createOutputBuffer(stack, (long) w * h * 4L);

            long descriptorSetLayout = createDescriptorSetLayout(stack);
            long pipelineLayout = createPipelineLayout(stack, descriptorSetLayout);
            long shaderModule = createShaderModule(stack, loadShader());
            long pipeline = createComputePipeline(stack, pipelineLayout, shaderModule);

            DescriptorResources descriptors = allocateAndUpdateDescriptors(stack, descriptorSetLayout, sceneParams,
                    materials, spheres, triangles, planes, dirLights, pointLights, bvhMin, bvhMax, bvhMeta, output);

            VkCommandBuffer commandBuffer = createCommandBuffer(stack);
            recordCommands(stack, commandBuffer, pipeline, pipelineLayout, descriptors.set);
            submitAndWait(stack, commandBuffer);

            BufferedImage image = readImage(output, w, h);
            System.out.println("Rendering complete.");

            // Cleanup
            vkDestroyPipeline(device, pipeline, null);
            vkDestroyShaderModule(device, shaderModule, null);
            vkDestroyPipelineLayout(device, pipelineLayout, null);
            vkDestroyDescriptorPool(device, descriptors.pool, null);
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);

            destroyBuffer(output);
            destroyBuffer(sceneParams);
            destroyBuffer(materials);
            destroyBuffer(spheres);
            destroyBuffer(triangles);
            destroyBuffer(planes);
            destroyBuffer(dirLights);
            destroyBuffer(pointLights);
            destroyBuffer(bvhMin);
            destroyBuffer(bvhMax);
            destroyBuffer(bvhMeta);

            return image;
        }
    }

    private void createInstance(MemoryStack stack) {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("Raytracer"))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("Raytracer"))
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_MAKE_VERSION(1, 2, 0));

        VkInstanceCreateInfo ci = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo);

        PointerBuffer pInstance = stack.mallocPointer(1);
        int err = vkCreateInstance(ci, null, pInstance);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan instance: " + err);
        }
        instance = new VkInstance(pInstance.get(0), ci);
    }

    private void pickPhysicalDevice(MemoryStack stack) {
        IntBuffer pCount = stack.mallocInt(1);
        vkEnumeratePhysicalDevices(instance, pCount, null);
        int count = pCount.get(0);
        if (count == 0) {
            throw new RuntimeException("No Vulkan physical devices found");
        }
        PointerBuffer devices = stack.mallocPointer(count);
        vkEnumeratePhysicalDevices(instance, pCount, devices);

        for (int i = 0; i < count; i++) {
            VkPhysicalDevice pd = new VkPhysicalDevice(devices.get(i), instance);
            int qIndex = findComputeQueueFamily(stack, pd);
            if (qIndex >= 0 && supportsPortability(stack, pd)) {
                physicalDevice = pd;
                queueFamilyIndex = qIndex;
                return;
            } else if (qIndex >= 0 && physicalDevice == null) { // fallback without portability
                physicalDevice = pd;
                queueFamilyIndex = qIndex;
            }
        }
        if (physicalDevice == null) {
            throw new RuntimeException("No suitable Vulkan device with compute queue");
        }
    }

    private boolean supportsPortability(MemoryStack stack, VkPhysicalDevice pd) {
        // On macOS MoltenVK requires portability subset; if not present we still continue.
        IntBuffer count = stack.mallocInt(1);
        vkEnumerateDeviceExtensionProperties(pd, (String) null, count, null);
        VkExtensionProperties.Buffer props = VkExtensionProperties.malloc(count.get(0), stack);
        vkEnumerateDeviceExtensionProperties(pd, (String) null, count, props);
        for (VkExtensionProperties p : props) {
            if (VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME.equals(p.extensionNameString())) {
                return true;
            }
        }
        return false;
    }

    private int findComputeQueueFamily(MemoryStack stack, VkPhysicalDevice pd) {
        IntBuffer count = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(pd, count, null);
        VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.malloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(pd, count, props);
        for (int i = 0; i < props.capacity(); i++) {
            if ((props.get(i).queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0) {
                return i;
            }
        }
        return -1;
    }

    private void createDeviceAndQueue(MemoryStack stack) {
        FloatBuffer priorities = stack.floats(1.0f);
        VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamilyIndex)
                .pQueuePriorities(priorities);

        VkPhysicalDeviceVulkan12Features features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                .bufferDeviceAddress(true);

        VkDeviceCreateInfo dci = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queues)
                .ppEnabledExtensionNames(enabledDeviceExtensions(stack))
                .pNext(features12);

        PointerBuffer pDev = stack.mallocPointer(1);
        int err = vkCreateDevice(physicalDevice, dci, null, pDev);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create device: " + err);
        }
        device = new VkDevice(pDev.get(0), physicalDevice, dci);
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
        queue = new VkQueue(pQueue.get(0), device);
    }

    private PointerBuffer enabledDeviceExtensions(MemoryStack stack) {
        List<String> exts = new ArrayList<>();
        exts.add("VK_KHR_shader_non_semantic_info"); // optional, safe
        // On macOS we need portability subset if available
        if (isMac()) {
            exts.add(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME);
        }
        PointerBuffer pb = stack.mallocPointer(exts.size());
        for (String e : exts) pb.put(stack.UTF8(e));
        pb.flip();
        return pb;
    }

    private boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
    }

    private void createCommandPool(MemoryStack stack) {
        VkCommandPoolCreateInfo ci = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .queueFamilyIndex(queueFamilyIndex)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        LongBuffer lp = stack.mallocLong(1);
        int err = vkCreateCommandPool(device, ci, null, lp);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create command pool: " + err);
        }
        commandPool = lp.get(0);
    }

    private VkBufferView createFloatBuffer(MemoryStack stack, float[] src) {
        ByteBuffer buf = floatArrayToBuffer(src);
        return createStorageBuffer(stack, buf.remaining(), buf, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }

    private VkBufferView createIntBuffer(MemoryStack stack, int[] src) {
        ByteBuffer buf = intArrayToBuffer(src);
        return createStorageBuffer(stack, buf.remaining(), buf, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }

    private VkBufferView createOutputBuffer(MemoryStack stack, long size) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Output buffer size too large for Java ByteBuffer mapping: " + size);
        }
        return createStorageBuffer(stack, size, null, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }

    private VkBufferView createStorageBuffer(MemoryStack stack, long size, ByteBuffer dataBuf, int usage) {
        VkBufferCreateInfo bi = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        LongBuffer pBuf = stack.mallocLong(1);
        int err = vkCreateBuffer(device, bi, null, pBuf);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create buffer: " + err);
        }
        long buffer = pBuf.get(0);

        VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
        vkGetBufferMemoryRequirements(device, buffer, req);
        int memType = findMemoryType(req.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        VkMemoryAllocateInfo mai = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(req.size())
                .memoryTypeIndex(memType);
        LongBuffer pMem = stack.mallocLong(1);
        err = vkAllocateMemory(device, mai, null, pMem);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate memory: " + err);
        }
        long memory = pMem.get(0);
        vkBindBufferMemory(device, buffer, memory, 0);

        if (dataBuf != null && dataBuf.remaining() > 0) {
            PointerBuffer pp = stack.mallocPointer(1);
            vkMapMemory(device, memory, 0, size, 0, pp);
            long addr = pp.get(0);
            MemoryUtil.memCopy(MemoryUtil.memAddress(dataBuf), addr, size);
            vkUnmapMemory(device, memory);
        }

        return new VkBufferView(buffer, memory, size);
    }

    private int findMemoryType(int typeBits, int flags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, props);
            for (int i = 0; i < props.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0 && (props.memoryTypes(i).propertyFlags() & flags) == flags) {
                    return i;
                }
            }
        }
        throw new RuntimeException("No suitable memory type");
    }

    private ByteBuffer packSceneParams(int tileX, int tileY, int tileW, int tileH) {
        ByteBuffer buf = MemoryUtil.memAlloc(176); // Increased size for tileOffset
        FloatBuffer f = buf.asFloatBuffer();
        // cam0
        f.put(data.camera[0]).put(data.camera[1]).put(data.camera[2]).put(0f);
        // cam1
        f.put(data.camera[3]).put(data.camera[4]).put(data.camera[5]).put(0f);
        // cam2
        f.put(data.camera[6]).put(data.camera[7]).put(data.camera[8]).put(data.camera[9]);
        // ortho U
        f.put(data.orthonormal[0]).put(data.orthonormal[1]).put(data.orthonormal[2]).put(0f);
        // ortho V
        f.put(data.orthonormal[3]).put(data.orthonormal[4]).put(data.orthonormal[5]).put(0f);
        // ortho W
        f.put(data.orthonormal[6]).put(data.orthonormal[7]).put(data.orthonormal[8]).put(0f);
        // ambient + maxDepth
        f.put(data.ambient[0]).put(data.ambient[1]).put(data.ambient[2]).put((float) data.maxDepth);
        // pixel params + width/height
        f.put(data.pixelParams[0]).put(data.pixelParams[1]).put((float) data.width).put((float) data.height);
        // counts0: sphere, tri, plane, materials
        f.put((float) data.sphereCount).put((float) data.triangleCount).put((float) data.planeCount).put((float) data.materialCount);
        // counts1: dir, point, bvhNodes, 0
        f.put((float) data.directionalLightCount).put((float) data.pointLightCount).put((float) data.bvhNodeCount).put(0f);
        // tileOffset: x, y, w, h
        f.put((float) tileX).put((float) tileY).put((float) tileW).put((float) tileH);
        
        buf.position(0);
        return buf;
    }

    private ByteBuffer floatArrayToBuffer(float[] src) {
        ByteBuffer buf = MemoryUtil.memAlloc(Math.max(1, src.length * Float.BYTES));
        buf.asFloatBuffer().put(src);
        buf.position(0);
        return buf;
    }

    private ByteBuffer intArrayToBuffer(int[] src) {
        ByteBuffer buf = MemoryUtil.memAlloc(Math.max(1, src.length * Integer.BYTES));
        buf.asIntBuffer().put(src);
        buf.position(0);
        return buf;
    }

    private long createDescriptorSetLayout(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(11, stack);
        for (int i = 0; i < bindings.capacity(); i++) {
            bindings.get(i)
                    .binding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        }
        bindings.position(0);

        VkDescriptorSetLayoutCreateInfo ci = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        LongBuffer p = stack.mallocLong(1);
        int err = vkCreateDescriptorSetLayout(device, ci, null, p);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor set layout: " + err);
        }
        return p.get(0);
    }

    private long createPipelineLayout(MemoryStack stack, long descriptorSetLayout) {
        VkPipelineLayoutCreateInfo ci = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer p = stack.mallocLong(1);
        int err = vkCreatePipelineLayout(device, ci, null, p);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create pipeline layout: " + err);
        }
        return p.get(0);
    }

    private long createShaderModule(MemoryStack stack, ByteBuffer spirv) {
        VkShaderModuleCreateInfo ci = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(spirv);
        LongBuffer p = stack.mallocLong(1);
        int err = vkCreateShaderModule(device, ci, null, p);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create shader module: " + err);
        }
        return p.get(0);
    }

    private long createComputePipeline(MemoryStack stack, long pipelineLayout, long shaderModule) {
        VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                .module(shaderModule)
                .pName(stack.UTF8("main"));

        VkComputePipelineCreateInfo.Buffer ci = VkComputePipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                .stage(stage)
                .layout(pipelineLayout);

        LongBuffer p = stack.mallocLong(1);
        int err = vkCreateComputePipelines(device, 0, ci, null, p);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create compute pipeline: " + err);
        }
        return p.get(0);
    }

    private DescriptorResources allocateAndUpdateDescriptors(MemoryStack stack, long layout,
                                                             VkBufferView sceneParams,
                                                             VkBufferView materials,
                                                             VkBufferView spheres,
                                                             VkBufferView triangles,
                                                             VkBufferView planes,
                                                             VkBufferView dirLights,
                                                             VkBufferView pointLights,
                                                             VkBufferView bvhMin,
                                                             VkBufferView bvhMax,
                                                             VkBufferView bvhMeta,
                                                             VkBufferView output) {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(11);

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);
        LongBuffer pPool = stack.mallocLong(1);
        int err = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor pool: " + err);
        }
        long pool = pPool.get(0);

        VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(pool)
                .pSetLayouts(stack.longs(layout));
        LongBuffer pSet = stack.mallocLong(1);
        err = vkAllocateDescriptorSets(device, ai, pSet);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate descriptor set: " + err);
        }
        long set = pSet.get(0);

        VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(11, stack);
        VkBufferView[] views = {sceneParams, materials, spheres, triangles, planes, dirLights, pointLights, bvhMin, bvhMax, bvhMeta, output};
        for (int i = 0; i < views.length; i++) {
            infos.get(i)
                    .buffer(views[i].buffer())
                    .offset(0)
                    .range(views[i].size());
        }
        infos.position(0);

        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(views.length, stack);
        for (int i = 0; i < views.length; i++) {
            writes.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(set)
                    .dstBinding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, infos.get(i)))
                    .descriptorCount(1);
        }
        writes.position(0);
        vkUpdateDescriptorSets(device, writes, null);

        return new DescriptorResources(pool, set);
    }

    private VkCommandBuffer createCommandBuffer(MemoryStack stack) {
        VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
        PointerBuffer p = stack.mallocPointer(1);
        int err = vkAllocateCommandBuffers(device, ai, p);
        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate command buffer: " + err);
        }
        return new VkCommandBuffer(p.get(0), device);
    }

    private void recordCommands(MemoryStack stack, VkCommandBuffer cmd, long pipeline, long pipelineLayout, long descriptorSet) {
        VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        vkBeginCommandBuffer(cmd, bi);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, stack.longs(descriptorSet), null);
        int groupsX = (data.width + LOCAL_SIZE_X - 1) / LOCAL_SIZE_X;
        int groupsY = (data.height + LOCAL_SIZE_Y - 1) / LOCAL_SIZE_Y;
        vkCmdDispatch(cmd, groupsX, groupsY, 1);
        vkEndCommandBuffer(cmd);
    }

    private void submitAndWait(MemoryStack stack, VkCommandBuffer cmd) {
        VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(cmd));
        vkQueueSubmit(queue, submit, VK_NULL_HANDLE);
        vkQueueWaitIdle(queue);
    }

    private BufferedImage readImage(VkBufferView output, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            vkMapMemory(device, output.memory(), 0, output.size(), 0, pp);
            ByteBuffer buf = MemoryUtil.memByteBuffer(pp.get(0), (int) output.size());
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = buf.get(idx) & 0xFF;
                    int g = buf.get(idx + 1) & 0xFF;
                    int b = buf.get(idx + 2) & 0xFF;
                    int rgb = (r << 16) | (g << 8) | b;
                    img.setRGB(x, height - 1 - y, rgb);
                    idx += 4;
                }
            }
            vkUnmapMemory(device, output.memory());
            return img;
        }
    }

    private ByteBuffer loadShader() {
        // Compile GLSL at runtime using shaderc
        String glsl = ShaderSources.RAYTRACING_COMP;
        long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
        long result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(
                compiler, glsl, org.lwjgl.util.shaderc.Shaderc.shaderc_compute_shader,
                "raytracing.comp", "main", 0);
        if (result == 0) {
            throw new RuntimeException("Shaderc compile failed");
        }
        ByteBuffer spirv = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes(result);
        ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
        copy.put(spirv).flip();
        org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
        org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
        return copy;
    }

    private void destroyBuffer(VkBufferView view) {
        vkDestroyBuffer(device, view.buffer(), null);
        vkFreeMemory(device, view.memory(), null);
        MemoryUtil.memFree(view.staging());
    }

    private record DescriptorResources(long pool, long set) {}

    /**
     * Simple holder for a buffer, its memory, and original staging content.
     */
    private static final class VkBufferView {
        private final long buffer;
        private final long memory;
        private final long size;
        private final ByteBuffer staging;
        VkBufferView(long buffer, long memory, long size) {
            this(buffer, memory, size, null);
        }
        VkBufferView(long buffer, long memory, long size, ByteBuffer staging) {
            this.buffer = buffer;
            this.memory = memory;
            this.size = size;
            this.staging = staging;
        }
        long buffer() { return buffer; }
        long memory() { return memory; }
        long size() { return size; }
        ByteBuffer staging() { return staging; }
    }
}

/**
 * GLSL source bundled inline to avoid resource loading issues.
 */
final class ShaderSources {
    private ShaderSources() {}

    static final String RAYTRACING_COMP = """
#version 460
layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

struct SceneParams {
    vec4 cam0;
    vec4 cam1;
    vec4 cam2;
    vec4 orthoU;
    vec4 orthoV;
    vec4 orthoW;
    vec4 ambientMaxDepth;
    vec4 pixel;
    vec4 counts0;
    vec4 counts1;
    vec4 tileOffset;
};

layout(std430, binding = 0) buffer Params { SceneParams params; };
layout(std430, binding = 1) buffer Materials { float materials[]; };
layout(std430, binding = 2) buffer Spheres { float spheres[]; };
layout(std430, binding = 3) buffer Triangles { float triangles[]; };
layout(std430, binding = 4) buffer Planes { float planes[]; };
layout(std430, binding = 5) buffer DirLights { float dirLights[]; };
layout(std430, binding = 6) buffer PointLights { float pointLights[]; };
layout(std430, binding = 7) buffer BvhMin { float bvhMin[]; };
layout(std430, binding = 8) buffer BvhMax { float bvhMax[]; };
layout(std430, binding = 9) buffer BvhMeta { uint bvhMeta[]; };
layout(std430, binding = 10) buffer Output { uint pixels[]; };

const uint NONE = 0xFFFFFFFFu;
const float EPSILON = 1e-3;
const float MAX_DIST = 1e30;

struct Hit {
    float t;
    vec3 p;
    vec3 n;
    uint matIdx;
};

bool intersectAabb(vec3 ro, vec3 rd, uint nodeIdx, out float tminOut) {
    uint off = nodeIdx * 4u;
    vec3 bmin = vec3(bvhMin[off], bvhMin[off+1u], bvhMin[off+2u]);
    vec3 bmax = vec3(bvhMax[off], bvhMax[off+1u], bvhMax[off+2u]);
    vec3 inv = 1.0 / rd;
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tminv = min(t0, t1);
    vec3 tmaxv = max(t0, t1);
    float tmin = max(max(tminv.x, tminv.y), tminv.z);
    float tmax = min(min(tmaxv.x, tmaxv.y), tmaxv.z);
    tminOut = tmin;
    return tmax >= max(tmin, 0.0);
}

bool intersectSphere(vec3 ro, vec3 rd, uint idx, inout Hit hit) {
    uint off = idx * 8u;
    vec3 c = vec3(spheres[off], spheres[off+1u], spheres[off+2u]);
    float r = spheres[off+3u];
    float b = dot(rd, ro - c);
    float cterm = dot(ro - c, ro - c) - r * r;
    float disc = b * b - cterm;
    if (disc < 0.0) return false;
    float t = -b - sqrt(disc);
    if (t < EPSILON) t = -b + sqrt(disc);
    if (t < EPSILON || t >= hit.t) return false;
    hit.t = t;
    hit.p = ro + t * rd;
    vec3 n = normalize(hit.p - c);
    // Ensure normal faces the ray origin (for inside-sphere hits)
    if (dot(n, rd) > 0.0) n = -n;
    hit.n = n;
    hit.matIdx = uint(spheres[off+4u]);
    return true;
}

bool intersectTriangle(vec3 ro, vec3 rd, uint idx, inout Hit hit) {
    uint off = idx * 12u;
    vec3 a = vec3(triangles[off], triangles[off+1u], triangles[off+2u]);
    vec3 b = vec3(triangles[off+3u], triangles[off+4u], triangles[off+5u]);
    vec3 c = vec3(triangles[off+6u], triangles[off+7u], triangles[off+8u]);
    vec3 ab = b - a;
    vec3 ac = c - a;
    vec3 pvec = cross(rd, ac);
    float det = dot(ab, pvec);
    if (abs(det) < 1e-6) return false;
    float invDet = 1.0 / det;
    vec3 tvec = ro - a;
    float u = dot(tvec, pvec) * invDet;
    if (u < 0.0 || u > 1.0) return false;
    vec3 qvec = cross(tvec, ab);
    float v = dot(rd, qvec) * invDet;
    if (v < 0.0 || u + v > 1.0) return false;
    float t = dot(ac, qvec) * invDet;
    if (t < EPSILON || t >= hit.t) return false;
    hit.t = t;
    hit.p = ro + t * rd;
    vec3 n = normalize(cross(ab, ac));
    // Ensure normal faces the ray origin
    if (dot(n, rd) > 0.0) n = -n;
    hit.n = n;
    hit.matIdx = uint(triangles[off+9u]);
    return true;
}

bool intersectPlane(vec3 ro, vec3 rd, uint idx, inout Hit hit) {
    uint off = idx * 8u;
    vec3 p0 = vec3(planes[off], planes[off+1u], planes[off+2u]);
    vec3 n = vec3(planes[off+3u], planes[off+4u], planes[off+5u]);
    float denom = dot(rd, n);
    if (abs(denom) < 1e-6) return false;
    float t = dot(p0 - ro, n) / denom;
    if (t < EPSILON || t >= hit.t) return false;
    hit.t = t;
    hit.p = ro + t * rd;
    // Ensure normal faces the ray origin
    vec3 nn = normalize(n);
    if (dot(nn, rd) > 0.0) nn = -nn;
    hit.n = nn;
    hit.matIdx = uint(planes[off+6u]);
    return true;
}

bool intersectBVH(vec3 ro, vec3 rd, out Hit outHit) {
    outHit.t = MAX_DIST;
    outHit.matIdx = 0u;
    outHit.p = vec3(0);
    outHit.n = vec3(0);
    if (uint(params.counts1.z) == 0u) return false;

    uint stack[64];
    int sp = 0;
    stack[sp++] = 0u;
    while (sp > 0) {
        uint node = stack[--sp];
        float tbox;
        if (!intersectAabb(ro, rd, node, tbox) || tbox > outHit.t) continue;
        uint off = node * 4u;
        uint leftIdx = bvhMeta[off];
        uint rightIdx = bvhMeta[off+1u];
        uint shapeType = bvhMeta[off+2u];
        uint shapeIdx = bvhMeta[off+3u];
        if (shapeType != NONE && shapeIdx != NONE) {
            Hit h = outHit;
            if (shapeType == 0u) { intersectSphere(ro, rd, shapeIdx, h); }
            else if (shapeType == 1u) { intersectTriangle(ro, rd, shapeIdx, h); }
            else if (shapeType == 2u) { intersectPlane(ro, rd, shapeIdx, h); }
            if (h.t < outHit.t) outHit = h;
        }
        if (leftIdx != NONE) stack[sp++] = leftIdx;
        if (rightIdx != NONE) stack[sp++] = rightIdx;
        if (sp >= 64) break;
    }
    return outHit.t < MAX_DIST;
}

bool intersectScene(vec3 ro, vec3 rd, out Hit outHit) {
    // Initialize/Check BVH first (resets outHit)
    intersectBVH(ro, rd, outHit);
    
    // Check unbounded planes
    uint planeCount = uint(params.counts0.z);
    for (uint i = 0u; i < planeCount; i++) {
        intersectPlane(ro, rd, i, outHit);
    }
    
    return outHit.t < MAX_DIST;
}

// Check if a point is in shadow from a light direction
bool isInShadow(vec3 origin, vec3 lightDir, float maxDist) {
    // 1. Check BVH
    if (uint(params.counts1.z) > 0u) {
        uint stack[64];
        int sp = 0;
        stack[sp++] = 0u;
        while (sp > 0) {
            uint node = stack[--sp];
            float tbox;
            if (!intersectAabb(origin, lightDir, node, tbox)) continue;
            if (tbox > maxDist) continue;
            
            uint off = node * 4u;
            uint leftIdx = bvhMeta[off];
            uint rightIdx = bvhMeta[off+1u];
            uint shapeType = bvhMeta[off+2u];
            uint shapeIdx = bvhMeta[off+3u];
            
            if (shapeType != NONE && shapeIdx != NONE) {
                Hit h;
                h.t = maxDist;
                h.matIdx = 0u;
                h.p = vec3(0);
                h.n = vec3(0);
                
                bool didHit = false;
                if (shapeType == 0u) { 
                    didHit = intersectSphere(origin, lightDir, shapeIdx, h); 
                }
                else if (shapeType == 1u) { 
                    didHit = intersectTriangle(origin, lightDir, shapeIdx, h); 
                }
                // Planes are not in BVH usually
                
                if (didHit && h.t > EPSILON && h.t < maxDist) {
                    return true; // Found an occluder
                }
            }
            if (leftIdx != NONE) stack[sp++] = leftIdx;
            if (rightIdx != NONE) stack[sp++] = rightIdx;
            if (sp >= 64) break;
        }
    }
    
    // 2. Check Planes
    uint planeCount = uint(params.counts0.z);
    for (uint i = 0u; i < planeCount; i++) {
        Hit h;
        h.t = maxDist;
        h.matIdx = 0u;
        // intersectPlane returns true if t < h.t (which is maxDist)
        if (intersectPlane(origin, lightDir, i, h)) {
            if (h.t > EPSILON && h.t < maxDist) {
                return true;
            }
        }
    }
    
    return false;
}

// Offset point along normal to avoid self-intersection
vec3 offsetPoint(vec3 p, vec3 n) {
    return p + n * EPSILON;
}

// Calculate reflection direction
vec3 reflectDir(vec3 incident, vec3 normal) {
    return incident - 2.0 * dot(incident, normal) * normal;
}

// Get material properties
void getMaterial(uint matIdx, out vec3 amb, out vec3 diff, out vec3 spec, out float shin) {
    uint o = matIdx * 12u;
    amb = vec3(materials[o], materials[o+1u], materials[o+2u]);
    diff = vec3(materials[o+3u], materials[o+4u], materials[o+5u]);
    spec = vec3(materials[o+6u], materials[o+7u], materials[o+8u]);
    shin = materials[o+9u];
}

// Shade a hit point (without reflections)
vec3 shade(Hit hit, vec3 wo) {
    vec3 amb, diff, spec;
    float shin;
    getMaterial(hit.matIdx, amb, diff, spec, shin);
    
    // Start with material ambient (not multiplied by scene ambient)
    vec3 color = amb;
    
    // Offset point along normal to avoid self-intersection
    vec3 shadowOrigin = hit.p + hit.n * EPSILON;
    
    // Directional lights with shadows
    for (uint i = 0u; i < uint(params.counts1.x); i++) {
        uint off = i * 8u;
        vec3 lightDir = -normalize(vec3(dirLights[off], dirLights[off+1u], dirLights[off+2u]));
        vec3 lc = vec3(dirLights[off+3u], dirLights[off+4u], dirLights[off+5u]);
        
        // Compute cosAngle (CPU uses max(dot, 0))
        float cosAngle = max(dot(hit.n, lightDir), 0.0);
        if (cosAngle <= 0.0) continue;
        
        // Check shadow (directional lights have infinite distance)
        if (!isInShadow(shadowOrigin, lightDir, MAX_DIST)) {
            // Diffuse: lightColor * materialDiffuse * cosAngle
            vec3 diffContrib = lc * diff * cosAngle;
            
            // Specular: lightColor * materialSpecular * pow(max(h.n, 0), shininess)
            vec3 h = normalize(lightDir + wo);
            float hdotn = max(dot(h, hit.n), 0.0);
            float specFactor = pow(hdotn, shin);
            vec3 specContrib = lc * spec * specFactor;
            
            color += diffContrib + specContrib;
        }
    }
    
    // Point lights with shadows
    for (uint i = 0u; i < uint(params.counts1.y); i++) {
        uint off = i * 8u;
        vec3 lp = vec3(pointLights[off], pointLights[off+1u], pointLights[off+2u]);
        vec3 lc = vec3(pointLights[off+3u], pointLights[off+4u], pointLights[off+5u]);
        vec3 toLight = lp - hit.p;
        float distToLight = length(toLight);
        vec3 lightDir = toLight / distToLight;
        
        // Compute cosAngle (CPU uses max(dot, 0))
        float cosAngle = max(dot(hit.n, lightDir), 0.0);
        if (cosAngle <= 0.0) continue;
        
        // Check shadow
        if (!isInShadow(shadowOrigin, lightDir, distToLight)) {
            // Diffuse: lightColor * materialDiffuse * cosAngle
            vec3 diffContrib = lc * diff * cosAngle;
            
            // Specular: lightColor * materialSpecular * pow(max(h.n, 0), shininess)
            vec3 h = normalize(lightDir + wo);
            float hdotn = max(dot(h, hit.n), 0.0);
            float specFactor = pow(hdotn, shin);
            vec3 specContrib = lc * spec * specFactor;
            
            color += diffContrib + specContrib;
        }
    }
    
    return color;
}

// Main ray tracing with reflections (iterative version of CPU's recursive traceRay)
// CPU logic:
//   if (depth <= 0) return scene.getAmbient();
//   if no hit: return black
//   result = mat.ambient + light contributions
//   if (depth > 1 && specular > 0): result += specular * traceRay(reflected, depth-1)
//   return result
vec3 traceRay(vec3 ro, vec3 rd, int maxDepth) {
    // We'll accumulate color iteratively
    // reflectionStack[i] = specular coefficient to multiply with next bounce's result
    vec3 accumulatedColor = vec3(0.0);
    vec3 reflectionCoeff = vec3(1.0);
    vec3 currentRo = ro;
    vec3 currentRd = rd;
    int currentDepth = maxDepth;
    
    for (int bounce = 0; bounce < 10; bounce++) { // max 10 bounces for safety
        // CPU: if (depth <= 0) return scene.getAmbient()
        if (currentDepth <= 0) {
            accumulatedColor += reflectionCoeff * params.ambientMaxDepth.rgb;
            break;
        }
        
        Hit hit;
        if (!intersectScene(currentRo, currentRd, hit)) {
            // CPU: if no hit, return black
            // accumulatedColor += reflectionCoeff * vec3(0.0); // adds nothing
            break;
        }
        
        vec3 wo = -currentRd;
        
        // Get material properties
        vec3 amb, diff, spec;
        float shin;
        getMaterial(hit.matIdx, amb, diff, spec, shin);
        
        // CPU: result = mat.ambient + light contributions
        vec3 localColor = shade(hit, wo);
        accumulatedColor += reflectionCoeff * localColor;
        
        // CPU: if (depth > 1 && specular > 0): continue with reflection
        bool hasSpecular = (spec.r > 0.0 || spec.g > 0.0 || spec.b > 0.0);
        if (currentDepth > 1 && hasSpecular) {
            // Prepare for next bounce
            currentRd = normalize(reflectDir(currentRd, hit.n));
            currentRo = hit.p + hit.n * EPSILON;
            reflectionCoeff *= spec;
            currentDepth--;
            
            // Early termination for very dim reflections
            float maxCoeff = max(max(reflectionCoeff.r, reflectionCoeff.g), reflectionCoeff.b);
            if (maxCoeff < 0.001) break;
        } else {
            // No more reflections
            break;
        }
    }
    
    return clamp(accumulatedColor, 0.0, 1.0);
}

void main() {
    ivec2 tileOffset = ivec2(params.tileOffset.xy);
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy) + tileOffset;
    int w = int(params.pixel.z);
    int h = int(params.pixel.w);
    
    // Check bounds against full image size
    if (gid.x >= w || gid.y >= h) return;

    // Check bounds against tile size (local invocation)
    // We need to write to the output buffer which is sized for the tile
    // The output buffer index should be based on local ID
    ivec2 lid = ivec2(gl_GlobalInvocationID.xy);
    int tileW = int(params.tileOffset.z);
    // int tileH = int(params.tileOffset.w); // Unused but available
    
    // If we are outside the tile buffer (should be handled by dispatch size, but safety check)
    if (lid.x >= tileW) return; 

    float pixelW = params.pixel.x;
    float pixelH = params.pixel.y;
    float a = pixelW * (float(gid.x) - float(w) * 0.5 + 0.5);
    float b = pixelH * (float(gid.y) - float(h) * 0.5 + 0.5);

    vec3 ro = params.cam0.xyz;
    vec3 dir = normalize(params.orthoU.xyz * a + params.orthoV.xyz * b - params.orthoW.xyz);

    int maxDepth = int(params.ambientMaxDepth.w);
    vec3 color = traceRay(ro, dir, maxDepth);

    // No gamma correction - match CPU behavior (direct linear to sRGB)
    uvec3 rgb8 = uvec3(clamp(color * 255.0 + 0.5, 0.0, 255.0));
    uint packed = (rgb8.r & 0xFFu) | ((rgb8.g & 0xFFu) << 8u) | ((rgb8.b & 0xFFu) << 16u) | 0xFF000000u;
    
    // Write to tile buffer using local index
    uint idx = uint(lid.y * tileW + lid.x);
    pixels[idx] = packed;
}
""";
}
