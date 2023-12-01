import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.json.simple.*;
import org.json.simple.parser.*;

import mdpa.dfd.datadictionary.*;
import mdpa.dfd.dataflowdiagram.*;

public class Parser{
	
	/**
	 * Creates dfd handover element from representation at file
	 * @param file Absolute Path to Representation
	 * @returns dfd handover element
	 */
	private dataflowdiagramFactory dfdFactory;
	private datadictionaryFactory ddFactory;
	private DataFlowDiagram dfd;
	private DataDictionary dd;
	
	private Map<String, Label> mapIdToLabel = new HashMap<String, Label>();
	private Map<String, Node> mapIdToNode = new HashMap<String, Node>();
	private Map<String, Pin> mapIdToPin = new HashMap<String, Pin>();
	private Map<String, Node> mapPinIdToNode = new HashMap<String, Node>();
	private Map<String, Pin> mapFlowNameToInputPin = new HashMap<String, Pin>();
	private Map<String, LabelType> mapNameToLabelType = new HashMap<String, LabelType>();
	
	
	public Parser () {		
		dfdFactory = dataflowdiagramFactory.eINSTANCE;
		ddFactory = datadictionaryFactory.eINSTANCE;
		
		dfd = dfdFactory.createDataFlowDiagram();
		dd = ddFactory.createDataDictionary();			
	}
	
	public void parseJson(String fileLocation) throws IOException, ParseException {
		Object obj = new JSONParser().parse(new FileReader(fileLocation));
		JSONObject jo = (JSONObject) obj;		
		JSONArray labelTypesJson = (JSONArray)jo.get("labelTypes");		
		JSONArray childrenJson = (JSONArray)((JSONObject)jo.get("model")).get("children");	
		
		parseLabelTypes(labelTypesJson);
		parseChildren (childrenJson);
	}
	
	private void parseChildren (JSONArray childrenJson) {
		for (Object childObject : childrenJson) {
			JSONObject child = (JSONObject) childObject;
			if (((String)child.get("type")).startsWith("node")) {
				parseNode(child);
			}			
		}
		for (Object childObject : childrenJson) {
			JSONObject child = (JSONObject) childObject;
			if (((String)child.get("type")).startsWith("edge")) {
				parseFlow(child);
			}			
		}
		for (Object childObject : childrenJson) {
			JSONObject child = (JSONObject) childObject;
			if (((String)child.get("type")).startsWith("node")) {
				annotateNodeBehaviour(child);
			}			
		}
	}
	
	private void parseNode(JSONObject child) {
		Node node;
		
		switch ((String)child.get("type")) {
			case "node:storage": node = dfdFactory.createStore(); break;			
			case "node:input-output": node = dfdFactory.createExternal(); break;
			default : node = dfdFactory.createProcess(); break;
		}
		
		String name = (String)child.get("text");
		String id = (String)child.get("id");
		
		node.setId(id);
		node.setEntityName(name);
		
		node.getProperties().addAll(parseProperties((JSONArray)child.get("labels")));
		node.setBehaviour(createBehaviour((JSONArray)child.get("ports"), node));
		
		mapIdToNode.put(id, node);
		
		dfd.getNodes().add(node);
	}
	
	
	private Behaviour createBehaviour(JSONArray ports, Node node) {
		Behaviour behaviour = ddFactory.createBehaviour();	
		behaviour.setEntityName(node.getEntityName() + "Behaviour");
		
		for (Object portObject : ports) {
			JSONObject portJson = (JSONObject) portObject;	
			String id = (String)portJson.get("id");			
			Pin pin = ddFactory.createPin();
			pin.setId(id);
			if (portJson.get("type").equals("port:dfd-input")) {
				pin.setEntityName(node.getEntityName() + "InPin");
				behaviour.getInPin().add(pin);
			} else {
				pin.setEntityName(node.getEntityName() + "OutPin");
				behaviour.getOutPin().add(pin);
			}
			mapIdToPin.put(id, pin);
			mapPinIdToNode.put(id, node);
		}
		
		dd.getBehaviour().add(behaviour);
		return behaviour;
	}
	
	private void annotateNodeBehaviour(JSONObject child) {
		String id = (String)child.get("id");
		Node node = mapIdToNode.get(id);
		Behaviour behaviour = node.getBehaviour();
		List<Pin> inputPins = new ArrayList<>();
		
		for (Object portObject : (JSONArray)child.get("ports")) {
			JSONObject portJson = (JSONObject) portObject;
			if (portJson.get("type").equals("port:dfd-input")) inputPins.add(mapIdToPin.get((String)portJson.get("id")));			
		}
		
		for (Object portObject : (JSONArray)child.get("ports")) {
			JSONObject portJson = (JSONObject) portObject;
			if (!portJson.get("type").equals("port:dfd-input")) {
				behaviour.getAssignment().addAll(parseBehaviour(portJson, inputPins));
			}			
		}
	}
	
	private List<AbstractAssignment> parseBehaviour(JSONObject port, List<Pin> inputPins) {
		List<AbstractAssignment> assignments = new ArrayList<>();
		
		Pin outputPin = mapIdToPin.get((String)port.get("id"));
		
		String behaviourString = (String)port.get("behavior");
		String[] behaviorDescriptions = behaviourString.split("\n");
		
		for (String behaviorDescription : behaviorDescriptions) {			
			if (behaviorDescription.startsWith("forward")) {
				String flow = behaviorDescription.split(" ")[1];
				ForwardingAssignment assignment = ddFactory.createForwardingAssignment();
				assignment.setOutputPin(outputPin);
				assignment.getInputPins().add(mapFlowNameToInputPin.get(flow));
				assignments.add(assignment);
			} else {
				String labelDescription = behaviorDescription.split(" ")[1];
				boolean value = Boolean.parseBoolean(behaviorDescription.split(" ")[3]);
				
				LabelType labelType = mapNameToLabelType.get(labelDescription.split("\\.")[0]);
				for (Label label : labelType.getLabel()) {
					if (label.getEntityName().equals(labelDescription.split("\\.")[1])) {
						Assignment assignment = ddFactory.createAssignment();
						assignment.setOutputPin(outputPin);
						assignment.getInputPins().addAll(inputPins);
						assignment.getOutputLabels().add(label);
						if (value) {
							assignment.setTerm(ddFactory.createTRUE());
						} else {
							NOT term = ddFactory.createNOT();
							term.setNegatedTerm(ddFactory.createTRUE());
							assignment.setTerm(term);
						}						
						assignments.add(assignment);
					}
				}
			}			
		}		
		return assignments;
	}
	
	private List<Label> parseProperties(JSONArray labelsJson) {
		List<Label> labels = new ArrayList<Label>();
		for (Object object : labelsJson) {
			JSONObject labelJson = (JSONObject) object;	
			labels.add(mapIdToLabel.get(labelJson.get("labelTypeValueId")));
		}
		return labels;
	}
	
	private void parseFlow(JSONObject child) {
		Flow flow = dfdFactory.createFlow();
		
		String name = (String)child.get("text");
		String sourcePinId = (String)child.get("sourceId");
		String destinationPinId = (String)child.get("targetId");
		
		flow.setEntityName(name);
		flow.setId((String)child.get("id"));
		flow.setSourceNode(mapPinIdToNode.get(sourcePinId));
		flow.setDestinationNode(mapPinIdToNode.get(destinationPinId));
		flow.setSourcePin(mapIdToPin.get(sourcePinId));
		flow.setDestinationPin(mapIdToPin.get(destinationPinId));	
		
		mapFlowNameToInputPin.put(name, mapIdToPin.get(destinationPinId));
		
		dfd.getFlows().add(flow);
	}
	
	private void parseLabelTypes (JSONArray labelTypesJson) {
		for (Object labelTypeObject : labelTypesJson) {
			JSONObject labelTypeJson = (JSONObject) labelTypeObject;
			
			LabelType labelType = ddFactory.createLabelType();
			String labelTypeName = (String)labelTypeJson.get("name");
			
			labelType.setId((String)labelTypeJson.get("id"));
			labelType.setEntityName(labelTypeName);
			for (Object labelObject : (JSONArray)labelTypeJson.get("values")) {
				JSONObject labelJson = (JSONObject) labelObject;
				Label label = ddFactory.createLabel();
				
				String labelId = (String)labelJson.get("id");
				label.setId(labelId);				
				label.setEntityName((String)labelJson.get("text"));
				
				mapIdToLabel.put(labelId, label);
				
				labelType.getLabel().add(label);
			}
			mapNameToLabelType.put(labelTypeName, labelType);
			dd.getLabelTypes().add(labelType);
		}	
	}

	public DataFlowDiagram getDfd() {
		return dfd;
	}

	public DataDictionary getDd() {
		return dd;
	}
	
}
