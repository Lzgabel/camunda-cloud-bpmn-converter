package cn.lzgabel.converter.processing.gateway;

import cn.lzgabel.converter.bean.BaseDefinition;
import cn.lzgabel.converter.bean.gateway.BranchNode;
import cn.lzgabel.converter.bean.gateway.ExclusiveGatewayDefinition;
import com.google.common.collect.Lists;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections.CollectionUtils;

/**
 * 〈功能简述〉<br>
 * 〈ExclusiveGateway节点类型详情设置〉
 *
 * @author lizhi
 * @since 1.0.0
 */
public class ExclusiveGatewayProcessor
    extends AbstractGatewayProcessor<ExclusiveGatewayDefinition, AbstractFlowNodeBuilder> {

  @Override
  public String onComplete(
      AbstractFlowNodeBuilder flowNodeBuilder, ExclusiveGatewayDefinition definition)
      throws InvocationTargetException, IllegalAccessException {
    ExclusiveGatewayBuilder exclusiveGatewayBuilder =
        flowNodeBuilder.exclusiveGateway().name(definition.getNodeName());
    List<BranchNode> branchNodes = definition.getBranchNodes();
    if (CollectionUtils.isEmpty(definition.getBranchNodes())
        && Objects.isNull(definition.getNextNode())) {
      return exclusiveGatewayBuilder.getElement().getId();
    }
    List<String> incoming = Lists.newArrayListWithCapacity(branchNodes.size());

    // 不存在任务节点的情况（即空分支: 见 branchNode-2）
    List<BranchNode> emptyNextNodeBranchNodes = Lists.newCopyOnWriteArrayList();
    for (BranchNode branchNode : branchNodes) {
      BaseDefinition nextNode = branchNode.getNextNode();

      String nodeName = branchNode.getNodeName();
      String expression = branchNode.getConditionExpression();

      // 记录分支条件中不存在任务节点的, 后续视情况补充对应的条件表达式, （见 branch-2）
      // ------------------------
      //
      //
      //           -(branch-1)-> serviceTask --
      // gateway ->                            -> gateway(merge) -> serviceTask(nextNode)
      //           -(branch-2)-> ----------- --
      //
      // ------------------------
      if (Objects.isNull(nextNode)) {
        incoming.add(exclusiveGatewayBuilder.getElement().getId());
        BranchNode condition =
            BranchNode.builder().nodeName(nodeName).conditionExpression(expression).build();
        emptyNextNodeBranchNodes.add(condition);
        continue;
      }

      // 只生成一个任务，同时设置当前任务的条件
      nextNode.setIncoming(Collections.singletonList(exclusiveGatewayBuilder.getElement().getId()));
      String id =
          onCreate(
              moveToNode(exclusiveGatewayBuilder, exclusiveGatewayBuilder.getElement().getId()),
              nextNode);
      exclusiveGatewayBuilder.getElement().getOutgoing().stream()
          .forEach(
              sequenceFlow -> conditionExpress(sequenceFlow, exclusiveGatewayBuilder, branchNode));
      if (Objects.nonNull(id)) {
        incoming.add(id);
      }
    }

    String id = exclusiveGatewayBuilder.getElement().getId();
    BaseDefinition nextNode = definition.getNextNode();
    if (Objects.nonNull(nextNode)) {
      nextNode.setIncoming(incoming);
      return merge(exclusiveGatewayBuilder, id, emptyNextNodeBranchNodes, nextNode);
    }
    return id;
  }
}
