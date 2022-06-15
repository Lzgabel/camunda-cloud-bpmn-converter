package cn.lzgabel.converter.processing;

import cn.lzgabel.converter.bean.BaseDefinition;
import cn.lzgabel.converter.bean.BpmnElementType;
import io.camunda.zeebe.model.bpmn.builder.AbstractBaseElementBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * 〈功能简述〉<br>
 * 〈完成基于 JSON 格式转 BPMN 元素业务逻辑转换〉
 *
 * @author lizhi
 * @since 1.0.0
 */
public interface BpmnElementProcessor<
    E extends BaseDefinition, T extends AbstractBaseElementBuilder> {

  String ZEEBE_EXPRESSION_PREFIX = "=";

  /**
   * 创建新的节点
   *
   * @param flowNodeBuilder builder
   * @param definition 流程节点参数
   * @return 最后一个节点id
   * @throws InvocationTargetException invocationTargetException
   * @throws IllegalAccessException illegalAccessException
   */
  default String onCreate(AbstractFlowNodeBuilder flowNodeBuilder, BaseDefinition definition)
      throws InvocationTargetException, IllegalAccessException {
    String nodeType = definition.getNodeType();
    BpmnElementType elementType = BpmnElementType.bpmnElementTypeFor(nodeType);
    BpmnElementProcessor<BaseDefinition, AbstractBaseElementBuilder> processor =
        BpmnElementProcessors.getProcessor(elementType);
    return processor.onComplete(flowNodeBuilder, definition);
  }

  /**
   * 完成当前节点详情设置
   *
   * @param builder builder
   * @param flowNode 流程节点参数
   * @return 最后一个节点id
   * @throws InvocationTargetException invocationTargetException
   * @throws IllegalAccessException illegalAccessException
   */
  String onComplete(T builder, E flowNode) throws InvocationTargetException, IllegalAccessException;

  /**
   * 循环向上转型, 获取对象的 DeclaredMethod
   *
   * @param object : 子类对象
   * @param methodName : 父类中的方法名
   * @param parameterTypes : 父类中的方法参数类型
   * @return 父类中的方法对象
   */
  default Method getDeclaredMethod(Object object, String methodName, Class<?>... parameterTypes) {
    Method method;
    for (Class<?> clazz = object.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
      try {
        method = clazz.getDeclaredMethod(methodName, parameterTypes);
        return method;
      } catch (Exception ignore) {
      }
    }
    return null;
  }

  /**
   * 移动到指定节点
   *
   * @param flowNodeBuilder builder
   * @param id 目标节点位移标识
   * @return 目标节点类型 builder
   */
  default AbstractFlowNodeBuilder<?, ?> moveToNode(
      AbstractFlowNodeBuilder<?, ?> flowNodeBuilder, String id) {
    return flowNodeBuilder.moveToNode(id);
  }

  /**
   * 创建指定类型实例
   *
   * @param flowNodeBuilder builder
   * @param nodeType 节点类型
   * @return 指定类型实例对象
   */
  @SuppressWarnings("unchecked")
  default Object createInstance(AbstractFlowNodeBuilder<?, ?> flowNodeBuilder, String nodeType) {
    // 自动生成id
    Method createTarget = getDeclaredMethod(flowNodeBuilder, "createTarget", Class.class);
    // 手动传入id
    // Method createTarget = getDeclaredMethod(abstractFlowNodeBuilder, "createTarget", Class.class,
    // String.class);

    try {
      createTarget.setAccessible(true);
      Class<? extends ModelElementInstance> clazz =
          BpmnElementType.bpmnElementTypeFor(nodeType)
              .getElementTypeClass()
              .orElseThrow(
                  () -> new RuntimeException("Unsupported BPMN element of type " + nodeType));
      return createTarget.invoke(flowNodeBuilder, clazz);
    } catch (IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
      return null;
    }
  }
}
